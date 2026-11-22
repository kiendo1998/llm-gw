package com.example.llmgw.service;

import com.example.llmgw.config.AudioStorageProperties;
import com.example.llmgw.domain.*;
import com.example.llmgw.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AudioStorageService {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final Pattern PART_FILENAME = Pattern.compile(
            "^(?<user>[^_]+)_(?<session>[A-Za-z0-9.-]+)_(?<timestamp>\\d+)_part(?<part>[1-9]\\d*)\\.(?<extension>[A-Za-z0-9]+)$",
            Pattern.CASE_INSENSITIVE);

    private final AudioUploadRepository uploads;
    private final AudioChunkRepository chunks;
    private final AudioStorageProperties properties;
    private final OutboxRepository outbox;

    public AudioStorageService(AudioUploadRepository uploads, AudioChunkRepository chunks,
                               AudioStorageProperties properties, OutboxRepository outbox) {
        this.uploads = uploads; this.chunks = chunks; this.properties = properties; this.outbox = outbox;
    }

    @Transactional
    public AudioIntake createOrGetByPartFilename(String originalFilename, String contentType) {
        var filename = safeFilename(originalFilename);
        var match = PART_FILENAME.matcher(filename);
        if (!match.matches()) {
            throw new IllegalArgumentException(
                    "X-File-Name must match user_session_timestamp_partN.extension");
        }
        var sessionId = match.group("session");
        var sequence = Integer.parseInt(match.group("part")) - 1;
        var outputFilename = match.group("user") + "_" + sessionId + "_" + match.group("timestamp")
                + "." + match.group("extension");
        var type = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        var existing = uploads.findBySessionId(sessionId);
        if (existing.isPresent()) {
            var upload = existing.get();
            if (!upload.getOriginalFilename().equals(outputFilename)) {
                throw new IllegalArgumentException("Session already exists with different file metadata");
            }
            upload.resume();
            return new AudioIntake(upload, sequence);
        }
        var upload = uploads.save(AudioUpload.create(UUID.randomUUID(), sessionId, outputFilename, type));
        createDirectories(uploadDirectory(upload).resolve(".parts"));
        return new AudioIntake(upload, sequence);
    }

    public record AudioIntake(AudioUpload upload, int sequence) {}

    @Transactional(readOnly = true)
    public AudioUpload get(UUID id) {
        return uploads.findById(id).orElseThrow(() -> new EntityNotFoundException("Audio upload not found: " + id));
    }

    @Transactional(readOnly = true)
    public AudioUpload getByIdOrSession(String reference) {
        var bySession = uploads.findBySessionId(reference);
        if (bySession.isPresent()) return bySession.get();
        try { return get(UUID.fromString(reference)); }
        catch (IllegalArgumentException ignored) {
            throw new EntityNotFoundException("Audio session not found: " + reference);
        }
    }

    @Transactional
    public AudioUpload enqueue(UUID id) {
        var upload = uploads.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Audio upload not found: " + id));
        upload.queued();
        var payload = "{\"audioId\":\"" + id + "\"}";
        outbox.save(OutboxEvent.create(properties.kafka().topic(), id, payload));
        return upload;
    }

    @Transactional
    public void markProcessing(UUID id) {
        var upload = uploads.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Audio upload not found: " + id));
        if (upload.getStatus() == AudioUploadStatus.QUEUED) upload.processing();
    }

    @Transactional
    public void fail(UUID id, String error) {
        uploads.findByIdForUpdate(id).ifPresent(upload -> upload.failed(error));
    }

    @Transactional
    public StoredPart storePart(UUID id, int sequence, InputStream input) {
        var upload = uploads.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Audio upload not found: " + id));
        if (upload.getStatus() != AudioUploadStatus.UPLOADING) {
            throw new IllegalStateException("Audio upload does not accept a new part yet");
        }
        var partDirectory = uploadDirectory(upload).resolve(".parts");
        createDirectories(partDirectory);
        var temp = partDirectory.resolve(sequence + ".part.tmp-" + UUID.randomUUID());
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (var output = new BufferedOutputStream(Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW));
                 var digestInput = new DigestInputStream(input, digest)) {
                size = copyWithLimit(digestInput, output, properties.storage().maxChunkBytes());
            }
            var sha256 = HexFormat.of().formatHex(digest.digest());
            var existing = chunks.findByUploadIdAndSequenceNumber(id, sequence);
            if (existing.isPresent()) {
                Files.deleteIfExists(temp);
                var saved = existing.get();
                if (saved.getSizeBytes() != size || !saved.getSha256().equals(sha256)) {
                    throw new IllegalArgumentException("Part " + (sequence + 1) + " was already uploaded with different content");
                }
                return new StoredPart(sequence, size, sha256, true);
            }
            var target = partDirectory.resolve(sequence + ".part");
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            chunks.save(AudioChunk.create(id, sequence, size, sha256, target.toAbsolutePath().toString()));
            upload.addChunk(size);
            return new StoredPart(sequence, size, sha256, false);
        } catch (IOException | NoSuchAlgorithmException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            throw new IllegalStateException("Cannot store audio chunk", e);
        } catch (RuntimeException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            throw e;
        }
    }

    public record StoredPart(int sequence, long sizeBytes, String sha256, boolean duplicate) {}

    @Transactional
    public AudioUpload completePending(UUID id) {
        var upload = uploads.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Audio upload not found: " + id));
        var storedChunks = chunks.findByUploadIdOrderBySequenceNumber(id);
        var directory = uploadDirectory(upload);
        var target = directory.resolve(upload.getOriginalFilename());
        var firstPending = upload.getLastSequence() == null ? 0 : upload.getLastSequence() + 1;
        var lastContiguous = firstPending - 1;
        for (var part : storedChunks) {
            if (part.getSequenceNumber() < firstPending) continue;
            if (part.getSequenceNumber() != lastContiguous + 1) break;
            lastContiguous = part.getSequenceNumber();
        }
        var originalSize = existingSize(target);
        try (var output = new BufferedOutputStream(Files.newOutputStream(target,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            for (var part : storedChunks) {
                if (part.getSequenceNumber() >= firstPending && part.getSequenceNumber() <= lastContiguous) {
                    Files.copy(Path.of(part.getStoragePath()), output);
                }
            }
        } catch (IOException e) {
            truncate(target, originalSize);
            throw new IllegalStateException("Cannot append audio part", e);
        }
        var allPartsApplied = !storedChunks.isEmpty()
                && lastContiguous == storedChunks.getLast().getSequenceNumber();
        upload.progress(lastContiguous, target.toAbsolutePath().toString(), allPartsApplied);
        return upload;
    }

    private long existingSize(Path path) {
        try { return Files.exists(path) ? Files.size(path) : 0; }
        catch (IOException e) { throw new IllegalStateException("Cannot read current audio size", e); }
    }

    private void truncate(Path path, long size) {
        try (var channel = java.nio.channels.FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.truncate(size);
        } catch (IOException ignored) {}
    }

    public Path completedFile(AudioUpload upload) {
        if (upload.getStatus() != AudioUploadStatus.COMPLETED || upload.getStoragePath() == null) {
            throw new IllegalStateException("Audio upload is not completed");
        }
        var path = Path.of(upload.getStoragePath());
        if (!Files.isRegularFile(path)) throw new EntityNotFoundException("Stored audio file is missing");
        return path;
    }

    @Scheduled(fixedDelayString = "${audio.upload.stale-scan-delay:1h}")
    @Transactional
    public void expireStaleUploads() {
        var cutoff = java.time.Instant.now().minus(properties.upload().staleAfter());
        for (var upload : uploads.findByStatusAndUpdatedAtBefore(AudioUploadStatus.UPLOADING, cutoff)) {
            upload.failed("Upload incomplete and inactive for " + properties.upload().staleAfter());
        }
    }

    private Path uploadDirectory(AudioUpload upload) {
        var folder = upload.getSessionId() == null ? upload.getId().toString() : upload.getSessionId();
        return properties.storage().root().toAbsolutePath().normalize().resolve(folder);
    }

    private String safeFilename(String original) {
        var filename = Path.of(original.replace('\\', '/')).getFileName().toString().trim();
        if (filename.isBlank() || filename.equals(".") || filename.equals("..")) {
            throw new IllegalArgumentException("Invalid original filename");
        }
        return filename;
    }

    private void createDirectories(Path directory) {
        try { Files.createDirectories(directory); }
        catch (IOException e) { throw new IllegalStateException("Cannot create audio storage directory", e); }
    }

    private long copyWithLimit(InputStream input, OutputStream output, long limit) throws IOException {
        var buffer = new byte[BUFFER_SIZE];
        long total = 0;
        for (int read; (read = input.read(buffer)) != -1;) {
            total += read;
            if (total > limit) throw new IllegalArgumentException("Audio chunk exceeds " + limit + " bytes");
            output.write(buffer, 0, read);
        }
        return total;
    }
}

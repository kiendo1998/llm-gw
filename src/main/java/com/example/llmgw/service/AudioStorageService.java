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

@Service
public class AudioStorageService {
    private static final int BUFFER_SIZE = 64 * 1024;

    private final AudioUploadRepository uploads;
    private final AudioChunkRepository chunks;
    private final AudioStorageProperties properties;
    private final OutboxRepository outbox;

    public AudioStorageService(AudioUploadRepository uploads, AudioChunkRepository chunks,
                               AudioStorageProperties properties, OutboxRepository outbox) {
        this.uploads = uploads; this.chunks = chunks; this.properties = properties; this.outbox = outbox;
    }

    @Transactional
    public AudioUpload createOrGet(UUID requestedId, String originalFilename, String contentType, int expectedChunks) {
        if (expectedChunks < 1 || expectedChunks > 100_000) {
            throw new IllegalArgumentException("X-Total-Chunks must be between 1 and 100000");
        }
        var filename = safeFilename(originalFilename);
        var type = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        var id = requestedId == null ? UUID.randomUUID() : requestedId;
        var existing = uploads.findById(id);
        if (existing.isPresent()) {
            var upload = existing.get();
            if (!upload.getOriginalFilename().equals(filename) || upload.getExpectedChunks() != expectedChunks) {
                throw new IllegalArgumentException("Audio ID already exists with different file metadata");
            }
            return upload;
        }
        var upload = uploads.save(AudioUpload.create(id, filename, type, expectedChunks));
        createDirectories(uploadDirectory(upload.getId()).resolve(".parts"));
        return upload;
    }

    @Transactional(readOnly = true)
    public AudioUpload get(UUID id) {
        return uploads.findById(id).orElseThrow(() -> new EntityNotFoundException("Audio upload not found: " + id));
    }

    @Transactional(readOnly = true)
    public UploadProgress progress(UUID id) {
        var upload = get(id);
        var stored = chunks.findByUploadIdOrderBySequenceNumber(id);
        int next = 0;
        for (var chunk : stored) {
            if (chunk.getSequenceNumber() == next) next++;
            else if (chunk.getSequenceNumber() > next) break;
        }
        var complete = stored.size() == upload.getExpectedChunks() && next == upload.getExpectedChunks();
        return new UploadProgress(upload, next, complete);
    }

    public record UploadProgress(AudioUpload upload, int nextSequence, boolean complete) {}

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
    public StoredChunk storeChunk(UUID id, int sequence, InputStream input) {
        if (sequence < 0) throw new IllegalArgumentException("Chunk sequence must be >= 0");
        var upload = uploads.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Audio upload not found: " + id));
        var existing = chunks.findByUploadIdAndSequenceNumber(id, sequence);
        if (existing.isPresent()) {
            var chunk = existing.get();
            return new StoredChunk(chunk.getSizeBytes(), chunk.getSha256(), true);
        }
        if (upload.getStatus() != AudioUploadStatus.UPLOADING) {
            throw new IllegalStateException("Audio upload no longer accepts new chunks");
        }
        if (sequence >= upload.getExpectedChunks()) {
            throw new IllegalArgumentException("Chunk sequence must be smaller than X-Total-Chunks");
        }

        var partDirectory = uploadDirectory(id).resolve(".parts");
        createDirectories(partDirectory);
        var temp = partDirectory.resolve(sequence + ".part.tmp-" + UUID.randomUUID());
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (var output = new BufferedOutputStream(Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW));
                 var digestInput = new DigestInputStream(input, digest)) {
                size = copyWithLimit(digestInput, output, properties.storage().maxChunkBytes());
            }
            var target = partDirectory.resolve(sequence + ".part");
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            var sha256 = HexFormat.of().formatHex(digest.digest());
            chunks.save(AudioChunk.create(id, sequence, size, sha256, target.toAbsolutePath().toString()));
            upload.addChunk(size);
            return new StoredChunk(size, sha256, false);
        } catch (IOException | NoSuchAlgorithmException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            throw new IllegalStateException("Cannot store audio chunk", e);
        } catch (RuntimeException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            throw e;
        }
    }

    public record StoredChunk(long sizeBytes, String sha256, boolean duplicate) {}

    @Transactional
    public AudioUpload complete(UUID id, int lastSequence) {
        var upload = uploads.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Audio upload not found: " + id));
        if (upload.getStatus() == AudioUploadStatus.COMPLETED) return upload;
        var storedChunks = chunks.findByUploadIdOrderBySequenceNumber(id);
        if (storedChunks.size() != lastSequence + 1) {
            throw new IllegalStateException("Expected chunks 0.." + lastSequence + " but found " + storedChunks.size());
        }
        for (int i = 0; i <= lastSequence; i++) {
            if (storedChunks.get(i).getSequenceNumber() != i) {
                throw new IllegalStateException("Missing audio chunk sequence " + i);
            }
        }

        var directory = uploadDirectory(id);
        var target = directory.resolve(upload.getOriginalFilename());
        if (storedChunks.size() == 1) {
            try {
                var source = Path.of(storedChunks.getFirst().getStoragePath());
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                Files.deleteIfExists(source.getParent());
                upload.complete(lastSequence, target.toAbsolutePath().toString());
                return upload;
            } catch (IOException e) {
                throw new IllegalStateException("Cannot finalize audio file", e);
            }
        }
        var temp = directory.resolve(upload.getOriginalFilename() + ".assembling");
        try (var output = new BufferedOutputStream(Files.newOutputStream(temp,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            for (var chunk : storedChunks) {
                Files.copy(Path.of(chunk.getStoragePath()), output);
            }
        } catch (IOException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            throw new IllegalStateException("Cannot assemble audio file", e);
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot finalize audio file", e);
        }
        upload.complete(lastSequence, target.toAbsolutePath().toString());
        return upload;
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

    private Path uploadDirectory(UUID id) {
        return properties.storage().root().toAbsolutePath().normalize().resolve(id.toString());
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

package com.example.llmgw.service;

import com.example.llmgw.api.audio.AudioChunkResponse;
import com.example.llmgw.config.AudioStorageProperties;
import com.example.llmgw.domain.*;
import com.example.llmgw.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public AudioStorageService(AudioUploadRepository uploads, AudioChunkRepository chunks,
                               AudioStorageProperties properties) {
        this.uploads = uploads; this.chunks = chunks; this.properties = properties;
    }

    @Transactional
    public AudioUpload create(String originalFilename, String contentType) {
        var filename = safeFilename(originalFilename);
        var type = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        var upload = uploads.save(AudioUpload.create(filename, type));
        createDirectories(uploadDirectory(upload.getId()).resolve(".parts"));
        return upload;
    }

    @Transactional(readOnly = true)
    public AudioUpload get(UUID id) {
        return uploads.findById(id).orElseThrow(() -> new EntityNotFoundException("Audio upload not found: " + id));
    }

    @Transactional
    public AudioChunkResponse storeChunk(UUID id, int sequence, InputStream input) {
        if (sequence < 0) throw new IllegalArgumentException("Chunk sequence must be >= 0");
        var upload = uploads.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Audio upload not found: " + id));
        if (upload.getStatus() != AudioUploadStatus.UPLOADING) {
            throw new IllegalStateException("Audio upload is already completed");
        }
        var existing = chunks.findByUploadIdAndSequenceNumber(id, sequence);
        if (existing.isPresent()) {
            var chunk = existing.get();
            return new AudioChunkResponse(id, sequence, chunk.getSizeBytes(), chunk.getSha256(), true);
        }

        var partDirectory = uploadDirectory(id).resolve(".parts");
        createDirectories(partDirectory);
        var temp = partDirectory.resolve(sequence + ".part.tmp-" + UUID.randomUUID());
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (var output = new BufferedOutputStream(Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW));
                 var digestInput = new DigestInputStream(input, digest)) {
                size = copyWithLimit(digestInput, output, properties.maxChunkBytes());
            }
            var target = partDirectory.resolve(sequence + ".part");
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            var sha256 = HexFormat.of().formatHex(digest.digest());
            chunks.save(AudioChunk.create(id, sequence, size, sha256, target.toAbsolutePath().toString()));
            upload.addChunk(size);
            return new AudioChunkResponse(id, sequence, size, sha256, false);
        } catch (IOException | NoSuchAlgorithmException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            throw new IllegalStateException("Cannot store audio chunk", e);
        } catch (RuntimeException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            throw e;
        }
    }

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

    private Path uploadDirectory(UUID id) {
        return properties.root().toAbsolutePath().normalize().resolve(id.toString());
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

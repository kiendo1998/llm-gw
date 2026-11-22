package com.example.llmgw.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audio_chunk", uniqueConstraints = @UniqueConstraint(columnNames = {"upload_id", "sequence_number"}))
public class AudioChunk {
    @Id private UUID id;
    @Column(name = "upload_id", nullable = false) private UUID uploadId;
    @Column(name = "sequence_number", nullable = false) private int sequenceNumber;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(name = "storage_path", nullable = false) private String storagePath;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected AudioChunk() {}

    public static AudioChunk create(UUID uploadId, int sequence, long size, String sha256, String path) {
        var chunk = new AudioChunk();
        chunk.id = UUID.randomUUID(); chunk.uploadId = uploadId; chunk.sequenceNumber = sequence;
        chunk.sizeBytes = size; chunk.sha256 = sha256; chunk.storagePath = path; chunk.createdAt = Instant.now();
        return chunk;
    }

    public int getSequenceNumber() { return sequenceNumber; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getStoragePath() { return storagePath; }
}

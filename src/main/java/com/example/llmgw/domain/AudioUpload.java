package com.example.llmgw.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audio_upload")
public class AudioUpload {
    @Id private UUID id;
    @Column(name = "original_filename", nullable = false) private String originalFilename;
    @Column(name = "content_type", nullable = false) private String contentType;
    @Enumerated(EnumType.STRING) private AudioUploadStatus status;
    @Column(name = "total_bytes") private long totalBytes;
    @Column(name = "chunk_count") private int chunkCount;
    @Column(name = "expected_chunks", nullable = false) private int expectedChunks;
    @Column(name = "last_sequence") private Integer lastSequence;
    @Column(name = "storage_path") private String storagePath;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "error_message") private String errorMessage;
    @Version private long version;

    protected AudioUpload() {}

    public static AudioUpload create(UUID id, String filename, String contentType) {
        var upload = new AudioUpload();
        upload.id = id;
        upload.originalFilename = filename;
        upload.contentType = contentType;
        upload.status = AudioUploadStatus.UPLOADING;
        upload.expectedChunks = 1;
        upload.createdAt = upload.updatedAt = Instant.now();
        return upload;
    }

    public void addChunk(long size) {
        if (status != AudioUploadStatus.UPLOADING) throw new IllegalStateException("Upload is already completed");
        totalBytes += size; chunkCount++; updatedAt = Instant.now();
    }

    public void resume() {
        if (status == AudioUploadStatus.UPLOADING) return;
        if (status != AudioUploadStatus.COMPLETED) {
            throw new IllegalStateException("Previous audio part is still being processed");
        }
        status = AudioUploadStatus.UPLOADING;
        completedAt = null;
        errorMessage = null;
        updatedAt = Instant.now();
    }

    public void queued() { status = AudioUploadStatus.QUEUED; updatedAt = Instant.now(); }
    public void processing() { status = AudioUploadStatus.PROCESSING; updatedAt = Instant.now(); }
    public void failed(String error) { status = AudioUploadStatus.FAILED; errorMessage = error; updatedAt = Instant.now(); }

    public void complete(int lastSequence, String path) {
        this.lastSequence = lastSequence; this.storagePath = path;
        this.status = AudioUploadStatus.COMPLETED;
        this.completedAt = this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public AudioUploadStatus getStatus() { return status; }
    public long getTotalBytes() { return totalBytes; }
    public int getChunkCount() { return chunkCount; }
    public int getExpectedChunks() { return expectedChunks; }
    public Integer getLastSequence() { return lastSequence; }
    public String getStoragePath() { return storagePath; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorMessage() { return errorMessage; }
}

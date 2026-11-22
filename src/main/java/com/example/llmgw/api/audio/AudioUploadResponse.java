package com.example.llmgw.api.audio;

import com.example.llmgw.domain.AudioUpload;

import java.time.Instant;
import java.util.UUID;

public record AudioUploadResponse(
        UUID audioId,
        String originalFilename,
        String contentType,
        String status,
        long totalBytes,
        int chunkCount,
        Integer lastSequence,
        String storagePath,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
    public static AudioUploadResponse from(AudioUpload upload) {
        return new AudioUploadResponse(upload.getId(), upload.getOriginalFilename(), upload.getContentType(),
                upload.getStatus().name(), upload.getTotalBytes(), upload.getChunkCount(),
                upload.getLastSequence(), upload.getStoragePath(), upload.getCreatedAt(),
                upload.getUpdatedAt(), upload.getCompletedAt());
    }
}

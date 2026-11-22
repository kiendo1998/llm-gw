package com.example.llmgw.api.audio;

import com.example.llmgw.domain.AudioUpload;

import java.time.Instant;
import java.util.UUID;

public record AudioStatusResponse(
        UUID audioId,
        String status,
        String originalFilename,
        String contentType,
        long totalBytes,
        int receivedChunks,
        int expectedChunks,
        int nextSequence,
        String error,
        Instant createdAt,
        Instant completedAt
) {
    public static AudioStatusResponse from(AudioUpload upload, int nextSequence) {
        return new AudioStatusResponse(upload.getId(), upload.getStatus().name(), upload.getOriginalFilename(),
                upload.getContentType(), upload.getTotalBytes(), upload.getChunkCount(), upload.getExpectedChunks(),
                nextSequence, upload.getErrorMessage(),
                upload.getCreatedAt(), upload.getCompletedAt());
    }
}

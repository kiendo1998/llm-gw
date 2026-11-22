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
        String error,
        Instant createdAt,
        Instant completedAt
) {
    public static AudioStatusResponse from(AudioUpload upload) {
        return new AudioStatusResponse(upload.getId(), upload.getStatus().name(), upload.getOriginalFilename(),
                upload.getContentType(), upload.getTotalBytes(), upload.getErrorMessage(),
                upload.getCreatedAt(), upload.getCompletedAt());
    }
}

package com.example.llmgw.api.audio;

import com.example.llmgw.domain.AudioUpload;

import java.util.UUID;

public record AudioStreamResponse(
        UUID audioId,
        int acceptedSequence,
        long acceptedBytes,
        boolean duplicate,
        String status,
        long totalBytes,
        int chunkCount,
        String originalFilename,
        String storagePath
) {
    public static AudioStreamResponse from(AudioUpload upload, int sequence, long acceptedBytes, boolean duplicate) {
        return new AudioStreamResponse(upload.getId(), sequence, acceptedBytes, duplicate,
                upload.getStatus().name(), upload.getTotalBytes(), upload.getChunkCount(),
                upload.getOriginalFilename(), upload.getStoragePath());
    }
}

package com.example.llmgw.api.audio;

import com.example.llmgw.domain.AudioUpload;

import java.util.UUID;

public record AudioUploadResult(
        UUID audioId,
        String status,
        String originalFilename,
        long totalBytes,
        int receivedParts,
        int acceptedPart,
        boolean async
) {
    public static AudioUploadResult from(AudioUpload upload, int acceptedPart, boolean async) {
        return new AudioUploadResult(upload.getId(), upload.getStatus().name(), upload.getOriginalFilename(),
                upload.getTotalBytes(), upload.getChunkCount(), acceptedPart, async);
    }
}

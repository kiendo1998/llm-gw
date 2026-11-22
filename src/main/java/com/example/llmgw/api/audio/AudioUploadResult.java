package com.example.llmgw.api.audio;

import com.example.llmgw.domain.AudioUpload;

import java.util.UUID;

public record AudioUploadResult(
        UUID audioId,
        String status,
        String originalFilename,
        long totalBytes,
        boolean async
) {
    public static AudioUploadResult from(AudioUpload upload, boolean async) {
        return new AudioUploadResult(upload.getId(), upload.getStatus().name(), upload.getOriginalFilename(),
                upload.getTotalBytes(), async);
    }
}

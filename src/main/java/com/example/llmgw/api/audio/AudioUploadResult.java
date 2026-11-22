package com.example.llmgw.api.audio;

import com.example.llmgw.domain.AudioUpload;

import java.util.UUID;

public record AudioUploadResult(
        UUID audioId,
        String status,
        String originalFilename,
        long totalBytes,
        int receivedChunks,
        int expectedChunks,
        int nextSequence,
        int acceptedSequence,
        boolean duplicate,
        boolean async
) {
    public static AudioUploadResult from(AudioUpload upload, int nextSequence, boolean async,
                                         int acceptedSequence, boolean duplicate) {
        return new AudioUploadResult(upload.getId(), upload.getStatus().name(), upload.getOriginalFilename(),
                upload.getTotalBytes(), upload.getChunkCount(), upload.getExpectedChunks(), nextSequence,
                acceptedSequence, duplicate, async);
    }
}

package com.example.llmgw.api.audio;

import jakarta.validation.constraints.NotBlank;

public record CreateAudioUploadRequest(
        @NotBlank String originalFilename,
        String contentType
) {}

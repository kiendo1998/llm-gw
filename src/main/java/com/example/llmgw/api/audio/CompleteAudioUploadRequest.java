package com.example.llmgw.api.audio;

import jakarta.validation.constraints.Min;

public record CompleteAudioUploadRequest(@Min(0) int lastSequence) {}

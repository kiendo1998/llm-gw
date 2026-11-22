package com.example.llmgw.api.audio;

import java.util.UUID;

public record AudioChunkResponse(UUID audioId, int sequence, long sizeBytes, String sha256, boolean duplicate) {}

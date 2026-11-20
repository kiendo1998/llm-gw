package com.example.llmgw.api;

import com.example.llmgw.domain.LlmTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(UUID taskId, String clientRequestId, String status, JsonNode result,
                           String error, Instant createdAt, Instant updatedAt) {
    public static TaskResponse from(LlmTask task, ObjectMapper mapper) {
        try {
            var result = task.getOutputJson() == null ? null : mapper.readTree(task.getOutputJson());
            return new TaskResponse(task.getId(), task.getClientRequestId(), task.getStatus().name(), result,
                    task.getErrorMessage(), task.getCreatedAt(), task.getUpdatedAt());
        } catch (Exception e) {
            throw new IllegalStateException("Stored output is not valid JSON", e);
        }
    }
}

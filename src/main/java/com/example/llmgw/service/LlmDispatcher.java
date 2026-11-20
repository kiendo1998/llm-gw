package com.example.llmgw.service;

import com.example.llmgw.config.GatewayProperties;
import com.example.llmgw.domain.TaskStatus;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Component
public class LlmDispatcher {
    private final TaskService taskService;
    private final WebClient llmWebClient;
    private final GatewayProperties properties;

    public LlmDispatcher(TaskService taskService, WebClient llmWebClient, GatewayProperties properties) {
        this.taskService = taskService; this.llmWebClient = llmWebClient; this.properties = properties;
    }

    @KafkaListener(topics = "${gateway.kafka.request-topic}", groupId = "llm-gw-dispatcher", concurrency = "6")
    public void dispatch(Map<String, Object> event) {
        var taskId = UUID.fromString(event.get("taskId").toString());
        var task = taskService.claimSlot(taskId);
        if (task.getStatus() != TaskStatus.DISPATCHING) return;
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("taskId", taskId.toString());
        parts.add("callbackUrl", properties.publicBaseUrl() + "/api/v1/llm/callbacks/" + taskId);
        parts.add("file", new ByteArrayResource(task.getInputJson().getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return taskId + ".json"; }
        });
        try {
            llmWebClient.post().contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(parts)).retrieve().toBodilessEntity()
                    .block(properties.llm().timeout());
            taskService.markProcessing(taskId);
        } catch (Exception e) {
            taskService.dispatchFailed(taskId, "LLM dispatch failed: " + rootMessage(e));
        }
    }

    private String rootMessage(Throwable e) {
        var current = e; while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}

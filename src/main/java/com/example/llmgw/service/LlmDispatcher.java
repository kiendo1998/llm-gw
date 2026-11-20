package com.example.llmgw.service;

import com.example.llmgw.config.GatewayProperties;
import com.example.llmgw.domain.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.UUID;

@Component
public class LlmDispatcher {
    private final TaskService taskService;
    private final WebClient llmWebClient;
    private final GatewayProperties properties;
    private final ObjectMapper mapper;

    public LlmDispatcher(TaskService taskService, WebClient llmWebClient, GatewayProperties properties,
                         ObjectMapper mapper) {
        this.taskService = taskService; this.llmWebClient = llmWebClient;
        this.properties = properties; this.mapper = mapper;
    }

    @KafkaListener(topics = "${gateway.kafka.request-topic}", groupId = "llm-gw-dispatcher", concurrency = "6")
    public void dispatch(Map<String, Object> event) {
        var taskId = UUID.fromString(event.get("taskId").toString());
        var task = taskService.claimSlot(taskId);
        if (task.getStatus() != TaskStatus.DISPATCHING) return;
        try {
            var body = mapper.createObjectNode()
                    .put("taskId", taskId.toString())
                    .put("callbackUrl", properties.publicBaseUrl() + "/api/v1/llm/callbacks/" + taskId)
                    .set("input", mapper.readTree(task.getInputJson()));
            llmWebClient.post().contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body).retrieve().toBodilessEntity()
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

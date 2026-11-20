package com.example.llmgw.api;

import com.example.llmgw.service.TaskService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/llm/callbacks")
public class LlmCallbackController {
    private final TaskService taskService;

    public LlmCallbackController(TaskService taskService) { this.taskService = taskService; }

    @PostMapping(value = "/{taskId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> callback(@PathVariable UUID taskId, @RequestBody String json) {
        taskService.complete(taskId, json);
        return ResponseEntity.noContent().build();
    }
}

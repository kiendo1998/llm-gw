package com.example.llmgw.api;

import com.example.llmgw.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    public TaskController(TaskService taskService, ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TaskResponse> submit(@RequestBody @NotNull String json,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        var task = taskService.submit(json, key);
        return ResponseEntity.accepted().location(URI.create("/api/v1/tasks/" + task.getId()))
                .body(TaskResponse.from(task, objectMapper));
    }

    @GetMapping("/{taskId}")
    TaskResponse get(@PathVariable UUID taskId) {
        return TaskResponse.from(taskService.get(taskId), objectMapper);
    }

    @GetMapping(value = "/{taskId}/result", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> result(@PathVariable UUID taskId) {
        var task = taskService.get(taskId);
        return task.getOutputJson() == null ? ResponseEntity.status(HttpStatus.ACCEPTED).build()
                : ResponseEntity.ok(task.getOutputJson());
    }
}

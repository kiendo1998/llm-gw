package com.example.llmgw.api;

import com.example.llmgw.service.TaskService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/llm/callbacks")
public class LlmCallbackController {
    private final TaskService taskService;

    public LlmCallbackController(TaskService taskService) { this.taskService = taskService; }

    @PostMapping(value = "/{taskId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Void> callback(@PathVariable UUID taskId, @RequestPart("file") MultipartFile file) throws IOException {
        taskService.complete(taskId, file.getBytes());
        return ResponseEntity.noContent().build();
    }
}

package com.example.llmgw.api;

import com.example.llmgw.domain.LlmTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskResponseTest {
    @Test
    void mapsNewTask() {
        var task = LlmTask.create("request-1", "{\"hello\":\"world\"}");
        var response = TaskResponse.from(task, new ObjectMapper());
        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(response.clientRequestId()).isEqualTo("request-1");
        assertThat(response.result()).isNull();
    }
}

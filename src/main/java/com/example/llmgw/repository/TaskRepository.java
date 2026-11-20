package com.example.llmgw.repository;

import com.example.llmgw.domain.LlmTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<LlmTask, UUID> {
    Optional<LlmTask> findByClientRequestId(String clientRequestId);
}

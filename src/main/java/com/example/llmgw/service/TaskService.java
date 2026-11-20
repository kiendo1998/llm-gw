package com.example.llmgw.service;

import com.example.llmgw.config.GatewayProperties;
import com.example.llmgw.domain.*;
import com.example.llmgw.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class TaskService {
    private final TaskRepository tasks;
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final GatewayProperties properties;
    @PersistenceContext private EntityManager entityManager;

    public TaskService(TaskRepository tasks, OutboxRepository outbox, ObjectMapper mapper,
                       GatewayProperties properties, EntityManager entityManager) {
        this.tasks = tasks; this.outbox = outbox; this.mapper = mapper;
        this.properties = properties; this.entityManager = entityManager;
    }

    @Transactional
    public LlmTask submit(byte[] bytes, String idempotencyKey) {
        var json = new String(bytes, StandardCharsets.UTF_8);
        try { mapper.readTree(json); } catch (Exception e) { throw new IllegalArgumentException("File is not valid JSON", e); }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = tasks.findByClientRequestId(idempotencyKey);
            if (existing.isPresent()) return existing.get();
        }
        var task = tasks.save(LlmTask.create(idempotencyKey, json));
        outbox.save(OutboxEvent.create(properties.kafka().requestTopic(), task.getId(), taskEvent(task, null)));
        return task;
    }

    @Transactional(readOnly = true)
    public LlmTask get(UUID id) { return tasks.findById(id).orElseThrow(() -> new EntityNotFoundException("Task not found: " + id)); }

    @Transactional
    public void complete(UUID id, byte[] bytes) {
        var output = new String(bytes, StandardCharsets.UTF_8);
        try { mapper.readTree(output); } catch (Exception e) { throw new IllegalArgumentException("Output file is not valid JSON", e); }
        var task = get(id);
        if (task.getStatus() == TaskStatus.COMPLETED) return;
        task.complete(output);
        releaseSlot(task);
        outbox.save(OutboxEvent.create(properties.kafka().resultTopic(), id, taskEvent(task, output)));
    }

    @Transactional
    public LlmTask claimSlot(UUID id) {
        var task = get(id);
        if (task.getStatus() != TaskStatus.QUEUED) return task;
        var rows = entityManager.createNativeQuery("""
                select id from llm_slot where task_id is null order by id limit 1 for update skip locked
                """).getResultList();
        if (rows.isEmpty()) throw new NoSlotAvailableException();
        int slot = ((Number) rows.getFirst()).intValue();
        entityManager.createNativeQuery("update llm_slot set task_id=:taskId, acquired_at=now() where id=:slot")
                .setParameter("taskId", id).setParameter("slot", slot).executeUpdate();
        task.markDispatching(slot);
        return task;
    }

    @Transactional
    public void markProcessing(UUID id) {
        var task = get(id);
        if (task.getStatus() == TaskStatus.DISPATCHING) task.markProcessing();
    }

    @Transactional
    public void dispatchFailed(UUID id, String message) {
        var task = get(id);
        if (task.getStatus() == TaskStatus.COMPLETED) return;
        task.fail(message); releaseSlot(task);
        outbox.save(OutboxEvent.create(properties.kafka().resultTopic(), id, taskEvent(task, null)));
    }

    private void releaseSlot(LlmTask task) {
        if (task.getSlotId() != null) entityManager.createNativeQuery("update llm_slot set task_id=null, acquired_at=null where id=:slot and task_id=:taskId")
                .setParameter("slot", task.getSlotId()).setParameter("taskId", task.getId()).executeUpdate();
    }

    private String taskEvent(LlmTask task, String result) {
        try {
            var node = mapper.createObjectNode().put("taskId", task.getId().toString()).put("status", task.getStatus().name());
            if (result != null) node.set("result", mapper.readTree(result));
            if (task.getErrorMessage() != null) node.put("error", task.getErrorMessage());
            return mapper.writeValueAsString(node);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}

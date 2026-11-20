package com.example.llmgw.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "llm_task")
public class LlmTask {
    protected LlmTask() {}
    @Id
    private UUID id;
    @Column(name = "client_request_id", unique = true)
    private String clientRequestId;
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    @Column(name = "input_json", columnDefinition = "jsonb", nullable = false)
    @ColumnTransformer(write = "cast(? as jsonb)")
    private String inputJson;
    @Column(name = "output_json", columnDefinition = "jsonb")
    @ColumnTransformer(write = "cast(? as jsonb)")
    private String outputJson;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "slot_id")
    private Integer slotId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    public static LlmTask create(String clientRequestId, String inputJson) {
        var task = new LlmTask();
        task.id = UUID.randomUUID();
        task.clientRequestId = clientRequestId;
        task.inputJson = inputJson;
        task.status = TaskStatus.QUEUED;
        task.createdAt = task.updatedAt = Instant.now();
        return task;
    }

    public void markDispatching(int slotId) { this.status = TaskStatus.DISPATCHING; this.slotId = slotId; touch(); }
    public void markProcessing() { this.status = TaskStatus.PROCESSING; touch(); }
    public void complete(String outputJson) { this.outputJson = outputJson; this.status = TaskStatus.COMPLETED; touch(); }
    public void fail(String message) { this.errorMessage = message; this.status = TaskStatus.FAILED; touch(); }
    private void touch() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getClientRequestId() { return clientRequestId; }
    public TaskStatus getStatus() { return status; }
    public String getInputJson() { return inputJson; }
    public String getOutputJson() { return outputJson; }
    public String getErrorMessage() { return errorMessage; }
    public Integer getSlotId() { return slotId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

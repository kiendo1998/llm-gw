package com.example.llmgw.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    protected OutboxEvent() {}
    @Id private UUID id;
    private String topic;
    @Column(name = "event_key") private String eventKey;
    @Column(columnDefinition = "jsonb") @ColumnTransformer(write = "cast(? as jsonb)") private String payload;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "published_at") private Instant publishedAt;

    public static OutboxEvent create(String topic, UUID taskId, String payload) {
        var event = new OutboxEvent();
        event.id = UUID.randomUUID(); event.topic = topic; event.eventKey = taskId.toString();
        event.payload = payload; event.createdAt = Instant.now();
        return event;
    }
    public void published() { this.publishedAt = Instant.now(); }
    public String getTopic() { return topic; }
    public String getEventKey() { return eventKey; }
    public String getPayload() { return payload; }
}

package com.example.llmgw.service;

import com.example.llmgw.repository.OutboxRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {
    private final OutboxRepository events;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(OutboxRepository events, KafkaTemplate<String, String> kafka) {
        this.events = events; this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${gateway.outbox-delay:500ms}")
    @Transactional
    public void publish() {
        for (var event : events.lockUnpublished()) {
            kafka.send(event.getTopic(), event.getEventKey(), event.getPayload()).join();
            event.published();
        }
    }
}

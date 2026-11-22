package com.example.llmgw.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class AudioStorageConsumer {
    private final AudioStorageService audioStorage;

    public AudioStorageConsumer(AudioStorageService audioStorage) { this.audioStorage = audioStorage; }

    @KafkaListener(topics = "${audio.kafka.topic}", groupId = "llm-gw-audio-storage")
    public void store(Map<String, Object> event) {
        var audioId = UUID.fromString(event.get("audioId").toString());
        try {
            audioStorage.markProcessing(audioId);
            audioStorage.complete(audioId, 0);
        } catch (Exception e) {
            audioStorage.fail(audioId, rootMessage(e));
        }
    }

    private String rootMessage(Throwable error) {
        var current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}

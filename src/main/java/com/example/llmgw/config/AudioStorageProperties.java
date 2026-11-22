package com.example.llmgw.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties("audio")
public record AudioStorageProperties(Storage storage, Kafka kafka, Upload upload) {
    public record Storage(Path root, long maxChunkBytes) {}
    public record Kafka(String topic) {}
    public record Upload(Duration staleAfter) {}
}

package com.example.llmgw.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("audio")
public record AudioStorageProperties(Storage storage, Kafka kafka) {
    public record Storage(Path root, long maxChunkBytes) {}
    public record Kafka(String topic) {}
}

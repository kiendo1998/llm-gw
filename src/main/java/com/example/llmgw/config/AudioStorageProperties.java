package com.example.llmgw.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("audio.storage")
public record AudioStorageProperties(Path root, long maxChunkBytes) {}

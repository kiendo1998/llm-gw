package com.example.llmgw.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("gateway")
public record GatewayProperties(Llm llm, Kafka kafka, String publicBaseUrl) {
    public record Llm(URI url, Duration timeout, int maxInFlight) {}
    public record Kafka(String requestTopic, String resultTopic) {}
}

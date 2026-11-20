package com.example.llmgw.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {
    @Bean
    WebClient llmWebClient(WebClient.Builder builder, GatewayProperties properties) {
        return builder.baseUrl(properties.llm().url().toString()).build();
    }

    @Bean
    NewTopic requestTopic(GatewayProperties p) {
        return TopicBuilder.name(p.kafka().requestTopic()).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic resultTopic(GatewayProperties p) {
        return TopicBuilder.name(p.kafka().resultTopic()).partitions(6).replicas(1).build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        // A busy global slot is normal back-pressure. Keep the Kafka record pending until one is free.
        return new DefaultErrorHandler(new FixedBackOff(1_000L, Long.MAX_VALUE));
    }
}

package com.ut.edu.backend.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka Configuration
 * Defines Kafka topics for the e-commerce platform.
 * Kept to 5 topics x <=2 partitions to fit Aiven for Apache Kafka's free
 * tier (max 5 topics, 2 partitions each) - order/payment sub-events share
 * one topic per domain and are dispatched by the "eventType" field already
 * present in every message, same pattern EMAIL_NOTIFICATION_TOPIC already used.
 */
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "spring.kafka.enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class KafkaConfig {

    public static final String ORDER_EVENTS_TOPIC = "order.events";
    public static final String PAYMENT_EVENTS_TOPIC = "payment.events";
    public static final String EMAIL_NOTIFICATION_TOPIC = "email.notification";
    public static final String INVENTORY_UPDATE_TOPIC = "inventory.update";
    public static final String SYSTEM_HEARTBEAT_TOPIC = "system.heartbeat";

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(ORDER_EVENTS_TOPIC)
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(PAYMENT_EVENTS_TOPIC)
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailNotificationTopic() {
        return TopicBuilder.name(EMAIL_NOTIFICATION_TOPIC)
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inventoryUpdateTopic() {
        return TopicBuilder.name(INVENTORY_UPDATE_TOPIC)
                .partitions(2)
                .replicas(1)
                .build();
    }

    // Written to by KafkaHeartbeatJob only - keeps managed brokers that
    // auto-shutdown on inactivity (e.g. Aiven's free tier) looking active.
    @Bean
    public NewTopic systemHeartbeatTopic() {
        return TopicBuilder.name(SYSTEM_HEARTBEAT_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}

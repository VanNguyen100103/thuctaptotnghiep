package com.ut.edu.backend.kafka;

import com.ut.edu.backend.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Keeps managed Kafka brokers that auto-shutdown on inactivity (e.g. Aiven's
 * free tier powers off after 24h with no produce/consume traffic, requiring
 * a manual power-on from its console) from ever looking idle. Without this,
 * EmailService's OTP/2FA/order-confirmation emails - which only fall back to
 * a direct send when Kafka is disabled entirely, not when the broker happens
 * to be unreachable - would silently stop delivering during a shutdown
 * window.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaHeartbeatJob {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedRate = 6 * 60 * 60 * 1000L)
    public void sendHeartbeat() {
        kafkaTemplate.send(KafkaConfig.SYSTEM_HEARTBEAT_TOPIC, "heartbeat", Instant.now().toString())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Kafka heartbeat failed - broker may be unreachable: {}", ex.getMessage());
                    } else {
                        log.debug("Kafka heartbeat sent");
                    }
                });
    }
}

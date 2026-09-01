package com.ut.edu.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} jobs (e.g. {@link com.ut.edu.backend.store.SubscriptionExpiryJob}).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

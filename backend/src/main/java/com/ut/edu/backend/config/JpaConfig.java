package com.ut.edu.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA Configuration
 * Enables auditing for automatic timestamp management
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.ut.edu.backend.repository")
@EnableTransactionManagement
public class JpaConfig {
}

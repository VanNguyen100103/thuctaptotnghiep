package com.ut.edu.backend.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson Configuration for handling Hibernate lazy loading proxies
 *
 * This configuration adds Hibernate6Module to the Jackson ObjectMapper
 * to properly serialize Hibernate entities with lazy-loaded relationships.
 *
 * Without this, attempting to serialize entities with lazy-loaded collections
 * or proxies will result in:
 * "No serializer found for class org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor"
 *
 * Note: Hibernate6Module has built-in Jakarta EE support for Spring Boot 3.x
 */
@Configuration
public class JacksonConfig {

    /**
     * Customize Jackson ObjectMapper with Hibernate6Module
     *
     * FORCE_LAZY_LOADING: Forces initialization of lazy-loaded properties during serialization
     * This ensures all data is loaded before JSON conversion
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // Register Hibernate6Module to handle Hibernate proxies and lazy collections
            Hibernate6Module hibernate6Module = new Hibernate6Module();

            // Force lazy loading before serialization
            // This will trigger database queries to load lazy relationships
            hibernate6Module.configure(Hibernate6Module.Feature.FORCE_LAZY_LOADING, true);

            builder.modulesToInstall(hibernate6Module);
        };
    }
}

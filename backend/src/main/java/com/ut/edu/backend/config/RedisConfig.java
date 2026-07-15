package com.ut.edu.backend.config;

import com.ut.edu.backend.user.User;
import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.category.Category;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Configuration
 * Configures Redis caching with different TTL for different cache types
 */
@Configuration
@EnableCaching
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${spring.cache.redis.flush-on-startup:false}")
    private boolean flushOnStartup;

    private final ObjectMapper objectMapper;
    private final RedisConnectionFactory redisConnectionFactory;

    // Constructor injection ensures ObjectMapper is fully configured before use
    public RedisConfig(ObjectMapper objectMapper, RedisConnectionFactory redisConnectionFactory) {
        this.objectMapper = objectMapper;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    /**
     * Auto flush Redis cache on application startup if enabled
     * Useful for development to ensure fresh cache after code changes
     * Set spring.cache.redis.flush-on-startup=true in application.properties
     */
    @PostConstruct
    public void flushRedisOnStartup() {
        if (flushOnStartup) {
            try {
                log.warn("========================================");
                log.warn("FLUSHING REDIS CACHE ON STARTUP");
                log.warn("========================================");

                redisConnectionFactory.getConnection()
                                      .serverCommands()
                                      .flushDb();

                log.warn("Redis cache FLUSHED successfully");
                log.warn("========================================");
            } catch (Exception e) {
                log.error("FAILED to flush Redis on startup: {}", e.getMessage(), e);
            }
        } else {
            log.info("Redis flush on startup is disabled. Set spring.cache.redis.flush-on-startup=true to enable.");
        }
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Use JSON serializer for values
        template.setValueSerializer(jackson2JsonRedisSerializer());
        template.setHashValueSerializer(jackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // Custom cache configurations with different TTL
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Products - 30 minutes (frequently updated)
        cacheConfigurations.put("products", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // Product searches - 15 minutes (dynamic queries)
        cacheConfigurations.put("productSearches", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // Active products list - 1 hour
        cacheConfigurations.put("activeProducts", defaultConfig.entryTtl(Duration.ofHours(1)));

        // Featured products - 2 hours (rarely changes)
        cacheConfigurations.put("featuredProducts", defaultConfig.entryTtl(Duration.ofHours(2)));

        // Best sellers - 1 hour
        cacheConfigurations.put("bestSellers", defaultConfig.entryTtl(Duration.ofHours(1)));

        // Newest products - 30 minutes
        cacheConfigurations.put("newestProducts", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // Sale products - 1 hour
        cacheConfigurations.put("saleProducts", defaultConfig.entryTtl(Duration.ofHours(1)));

        // Category products - 1 hour
        cacheConfigurations.put("categoryProducts", defaultConfig.entryTtl(Duration.ofHours(1)));

        // Categories - 6 hours (rarely changes)
        cacheConfigurations.put("categories", defaultConfig.entryTtl(Duration.ofHours(6)));

        // Brands, sizes, colors - 12 hours (static data)
        cacheConfigurations.put("brands", defaultConfig.entryTtl(Duration.ofHours(12)));
        cacheConfigurations.put("sizes", defaultConfig.entryTtl(Duration.ofHours(12)));
        cacheConfigurations.put("colors", defaultConfig.entryTtl(Duration.ofHours(12)));

        // User data - 30 minutes (for security)
        cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // Shopping carts - 24 hours
        cacheConfigurations.put("carts", defaultConfig.entryTtl(Duration.ofHours(24)));

        // Orders - 1 hour
        cacheConfigurations.put("orders", defaultConfig.entryTtl(Duration.ofHours(1)));

        // Reviews - 2 hours
        cacheConfigurations.put("reviews", defaultConfig.entryTtl(Duration.ofHours(2)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    private GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer() {
        // Use the injected ObjectMapper which already has Hibernate6Module configured
        ObjectMapper mapper = objectMapper.copy();

        // Enable default typing for polymorphic deserialization
        mapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );

        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}

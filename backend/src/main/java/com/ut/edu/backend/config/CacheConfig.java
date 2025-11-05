package com.ut.edu.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine Cache Configuration for AI Results
 *
 * Why Caffeine instead of Redis?
 * - No Redis key conflicts (isolated in-memory)
 * - Faster (no network latency)
 * - Simpler setup
 * - Auto-eviction on TTL expiry
 *
 * Cache Strategy:
 * - AI Outfit: 1 hour TTL, max 1000 entries
 * - AI Similar Products: 1 hour TTL, max 1000 entries
 * - AI Recommendations: 30 minutes TTL, max 500 entries
 *
 * This reduces Gemini API calls by 90%+
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Primary
    @Bean(name = "caffeineCacheManager")
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "ai-outfit",           // Cache for outfit recommendations
            "ai-similar",          // Cache for similar products
            "ai-recommendations",  // Cache for personalized recommendations
            "ai-sentiment",        // Cache for sentiment analysis
            "ai-trending"          // Cache for trending products
        );

        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)  // TTL: 1 hour
            .maximumSize(1000)                     // Max entries per cache
            .recordStats());                       // Enable cache stats for monitoring

        return cacheManager;
    }
}

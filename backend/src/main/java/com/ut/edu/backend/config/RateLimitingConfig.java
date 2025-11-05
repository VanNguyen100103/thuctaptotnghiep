package com.ut.edu.backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting Configuration using Bucket4j
 * Implements token bucket algorithm for API rate limiting
 */
@Configuration
public class RateLimitingConfig {

    /**
     * Storage for IP-based rate limit buckets
     * Key: IP address
     * Value: Bucket for that IP
     */
    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    /**
     * Storage for endpoint-specific rate limit buckets
     * Key: IP + Endpoint combination
     * Value: Bucket for that combination
     */
    private final Map<String, Bucket> endpointBucketCache = new ConcurrentHashMap<>();

    /**
     * Get or create a bucket for general API access
     * Rate limit: 100 requests per minute per IP
     */
    public Bucket resolveBucket(String key) {
        return bucketCache.computeIfAbsent(key, k -> createDefaultBucket());
    }

    /**
     * Get or create a bucket for authentication endpoints
     * Rate limit: 5 requests per minute per IP (strict for security)
     */
    public Bucket resolveAuthBucket(String key) {
        return endpointBucketCache.computeIfAbsent("auth:" + key, k -> createAuthBucket());
    }

    /**
     * Get or create a bucket for registration endpoint
     * Rate limit: 3 requests per hour per IP (prevent spam accounts)
     */
    public Bucket resolveRegistrationBucket(String key) {
        return endpointBucketCache.computeIfAbsent("register:" + key, k -> createRegistrationBucket());
    }

    /**
     * Get or create a bucket for password reset endpoint
     * Rate limit: 3 requests per hour per IP (prevent email flooding)
     */
    public Bucket resolvePasswordResetBucket(String key) {
        return endpointBucketCache.computeIfAbsent("password-reset:" + key, k -> createPasswordResetBucket());
    }

    /**
     * Get or create a bucket for search endpoints
     * Rate limit: 30 requests per minute per IP
     */
    public Bucket resolveSearchBucket(String key) {
        return endpointBucketCache.computeIfAbsent("search:" + key, k -> createSearchBucket());
    }

    /**
     * Get or create a bucket for order endpoints
     * Rate limit: 10 requests per minute per IP
     */
    public Bucket resolveOrderBucket(String key) {
        return endpointBucketCache.computeIfAbsent("order:" + key, k -> createOrderBucket());
    }

    /**
     * Create default bucket: 100 requests per minute
     */
    private Bucket createDefaultBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(100)
                .refillIntervally(100, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Create auth bucket: 5 requests per minute
     */
    private Bucket createAuthBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(5)
                .refillIntervally(5, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Create registration bucket: 3 requests per hour
     */
    private Bucket createRegistrationBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(3)
                .refillIntervally(3, Duration.ofHours(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Create password reset bucket: 3 requests per hour
     */
    private Bucket createPasswordResetBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(3)
                .refillIntervally(3, Duration.ofHours(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Create search bucket: 30 requests per minute
     */
    private Bucket createSearchBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(30)
                .refillIntervally(30, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Create order bucket: 10 requests per minute
     */
    private Bucket createOrderBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(10)
                .refillIntervally(10, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Clear all buckets (useful for testing)
     */
    public void clearAllBuckets() {
        bucketCache.clear();
        endpointBucketCache.clear();
    }

    /**
     * Clear bucket for specific key
     */
    public void clearBucket(String key) {
        bucketCache.remove(key);
        endpointBucketCache.keySet().removeIf(k -> k.contains(key));
    }
}

package com.ut.edu.backend.service.impl;

import com.ut.edu.backend.model.Cart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis Cart Cache Service
 * Manages shopping cart caching for fast access
 * Pattern tương tự RedisUserSessionService và RedisProductCacheService
 */
@Service
@Slf4j
public class RedisCartCacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Cache TTL: 24 hours (giống cart session)
    private static final long CART_TTL = 24;

    /**
     * Cache cart data for user
     */
    public void cacheCart(Long userId, Cart cart) {
        if (redisTemplate != null && userId != null && cart != null) {
            try {
                String key = "cart:" + userId;

                // Lưu cart object với các thông tin quan trọng
                Map<String, Object> cartData = new HashMap<>();
                cartData.put("cartId", cart.getId());
                cartData.put("userId", userId);
                cartData.put("totalItems", cart.getTotalItems());
                cartData.put("totalPrice", cart.getTotalPrice());
                cartData.put("active", cart.getActive());
                cartData.put("items", cart.getItems()); // Full cart items
                cartData.put("cachedAt", System.currentTimeMillis());

                redisTemplate.opsForValue().set(key, cartData, CART_TTL, TimeUnit.HOURS);
                log.debug("✓ Cart cached: userId={}, cartId={}, items={}, total={}",
                        userId, cart.getId(), cart.getTotalItems(), cart.getTotalPrice());

            } catch (Exception e) {
                log.warn("Failed to cache cart (non-critical): {}", e.getMessage());
            }
        }
    }

    /**
     * Get cached cart data for user
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCachedCart(Long userId) {
        if (redisTemplate != null && userId != null) {
            try {
                String key = "cart:" + userId;
                Object cached = redisTemplate.opsForValue().get(key);

                if (cached instanceof Map) {
                    log.debug("✓ Cart cache HIT: userId={}", userId);
                    return (Map<String, Object>) cached;
                }
            } catch (Exception e) {
                log.warn("Failed to get cached cart (non-critical): {}", e.getMessage());
            }
        }
        log.debug("✗ Cart cache MISS: userId={}", userId);
        return null;
    }

    /**
     * Invalidate cart cache when cart is modified
     */
    public void invalidateCart(Long userId) {
        if (redisTemplate != null && userId != null) {
            try {
                // Delete cart data cache
                String cartKey = "cart:" + userId;
                redisTemplate.delete(cartKey);

                // Also delete cart count cache
                String countKey = "cart:count:" + userId;
                redisTemplate.delete(countKey);

                log.info("✓ Cart cache invalidated: userId={} (cart + count)", userId);
            } catch (Exception e) {
                log.warn("Failed to invalidate cart cache (non-critical): {}", e.getMessage());
            }
        }
    }

    /**
     * Cache cart item count only (lightweight cache)
     */
    public void cacheCartCount(Long userId, Integer count) {
        if (redisTemplate != null && userId != null) {
            try {
                String key = "cart:count:" + userId;
                redisTemplate.opsForValue().set(key, count, CART_TTL, TimeUnit.HOURS);
                log.debug("✓ Cart count cached: userId={}, count={}", userId, count);
            } catch (Exception e) {
                log.warn("Failed to cache cart count (non-critical): {}", e.getMessage());
            }
        }
    }

    /**
     * Get cached cart item count
     */
    public Integer getCachedCartCount(Long userId) {
        if (redisTemplate != null && userId != null) {
            try {
                String key = "cart:count:" + userId;
                Object cached = redisTemplate.opsForValue().get(key);
                if (cached instanceof Integer) {
                    log.debug("✓ Cart count cache HIT: userId={}", userId);
                    return (Integer) cached;
                }
            } catch (Exception e) {
                log.warn("Failed to get cached cart count (non-critical): {}", e.getMessage());
            }
        }
        log.debug("✗ Cart count cache MISS: userId={}", userId);
        return null;
    }

    /**
     * Update cart cache after modification
     * Use this when cart items change (add/update/remove)
     */
    public void updateCartCache(Long userId, Cart cart) {
        // Invalidate old cache
        invalidateCart(userId);

        // Cache new cart data
        cacheCart(userId, cart);
    }
}

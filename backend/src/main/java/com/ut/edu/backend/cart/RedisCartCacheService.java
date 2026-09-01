package com.ut.edu.backend.cart;

import com.ut.edu.backend.auth.RedisUserSessionService;
import com.ut.edu.backend.product.RedisProductCacheService;

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
     * Keys are namespaced per tenant (cart:{storeId}:{userId}) so two stores
     * never share cache entries. storeId is passed explicitly by callers -
     * TenantContext isn't populated for these requests (Cart's routes aren't
     * under /stores/{slug}/**, and customer JWTs carry no storeId claim), so
     * reading it here would always resolve to "no tenant" and collide across
     * stores.
     */
    private String cartKey(Long userId, Long storeId) {
        return "cart:" + storeId + ":" + userId;
    }

    private String cartCountKey(Long userId, Long storeId) {
        return "cart:count:" + storeId + ":" + userId;
    }

    /**
     * Cache cart data for user
     */
    public void cacheCart(Long userId, Long storeId, Cart cart) {
        if (redisTemplate != null && userId != null && cart != null) {
            try {
                String key = cartKey(userId, storeId);

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
    public Map<String, Object> getCachedCart(Long userId, Long storeId) {
        if (redisTemplate != null && userId != null) {
            try {
                String key = cartKey(userId, storeId);
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
    public void invalidateCart(Long userId, Long storeId) {
        if (redisTemplate != null && userId != null) {
            try {
                // Delete cart data cache
                redisTemplate.delete(cartKey(userId, storeId));

                // Also delete cart count cache
                redisTemplate.delete(cartCountKey(userId, storeId));

                log.info("✓ Cart cache invalidated: userId={} (cart + count)", userId);
            } catch (Exception e) {
                log.warn("Failed to invalidate cart cache (non-critical): {}", e.getMessage());
            }
        }
    }

    /**
     * Cache cart item count only (lightweight cache)
     */
    public void cacheCartCount(Long userId, Long storeId, Integer count) {
        if (redisTemplate != null && userId != null) {
            try {
                String key = cartCountKey(userId, storeId);
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
    public Integer getCachedCartCount(Long userId, Long storeId) {
        if (redisTemplate != null && userId != null) {
            try {
                String key = cartCountKey(userId, storeId);
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
    public void updateCartCache(Long userId, Long storeId, Cart cart) {
        // Invalidate old cache
        invalidateCart(userId, storeId);

        // Cache new cart data
        cacheCart(userId, storeId, cart);
    }
}

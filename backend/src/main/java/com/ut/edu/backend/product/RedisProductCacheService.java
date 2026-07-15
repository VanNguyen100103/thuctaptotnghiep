package com.ut.edu.backend.product;

import com.ut.edu.backend.auth.RedisUserSessionService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis Product Cache Service
 * Manages product caching with manual control using RedisTemplate
 * Similar pattern to RedisUserSessionService
 */
@Service
@Slf4j
public class RedisProductCacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Cache TTL constants (in minutes)
    private static final long PRODUCT_TTL = 30; // 30 minutes
    private static final long PRODUCT_LIST_TTL = 15; // 15 minutes for searches
    private static final long FEATURED_TTL = 120; // 2 hours
    private static final long BESTSELLERS_TTL = 60; // 1 hour
    private static final long FILTERS_TTL = 720; // 12 hours

    /**
     * Cache single product by ID
     */
    public void cacheProduct(Product product) {
        if (redisTemplate != null && product != null) {
            try {
                String key = "product:" + product.getId();
                redisTemplate.opsForValue().set(key, product, PRODUCT_TTL, TimeUnit.MINUTES);
                log.debug("✓ Product cached: id={}, name={}", product.getId(), product.getName());
            } catch (Exception e) {
                log.warn("Failed to cache product (non-critical): {}", e.getMessage());
            }
        }
    }

    /**
     * Cache single product by slug
     */
    public void cacheProductBySlug(String slug, Product product) {
        if (redisTemplate != null && product != null && slug != null) {
            try {
                String key = "product:slug:" + slug;
                redisTemplate.opsForValue().set(key, product, PRODUCT_TTL, TimeUnit.MINUTES);
                log.debug("✓ Product cached by slug: slug={}, id={}", slug, product.getId());
            } catch (Exception e) {
                log.warn("Failed to cache product by slug (non-critical): {}", e.getMessage());
            }
        }
    }

    /**
     * Get cached product by ID
     */
    public Product getCachedProduct(Long productId) {
        if (redisTemplate != null && productId != null) {
            try {
                String key = "product:" + productId;
                Object cached = redisTemplate.opsForValue().get(key);
                if (cached instanceof Product) {
                    log.debug("✓ Product cache HIT: id={}", productId);
                    return (Product) cached;
                }
            } catch (Exception e) {
                log.warn("Failed to get cached product (non-critical): {}", e.getMessage());
            }
        }
        log.debug("✗ Product cache MISS: id={}", productId);
        return null;
    }

    /**
     * Get cached product by slug
     */
    public Product getCachedProductBySlug(String slug) {
        if (redisTemplate != null && slug != null) {
            try {
                String key = "product:slug:" + slug;
                Object cached = redisTemplate.opsForValue().get(key);
                if (cached instanceof Product) {
                    log.debug("✓ Product cache HIT: slug={}", slug);
                    return (Product) cached;
                }
            } catch (Exception e) {
                log.warn("Failed to get cached product by slug (non-critical): {}", e.getMessage());
            }
        }
        log.debug("✗ Product cache MISS: slug={}", slug);
        return null;
    }

    /**
     * Cache product search results (complex query)
     */
    public void cacheSearchResults(String searchKey, Page<Product> searchResults) {
        if (redisTemplate != null && searchKey != null && searchResults != null) {
            try {
                String key = "search:" + searchKey;

                // Store search result metadata + product list
                Map<String, Object> searchData = new HashMap<>();
                searchData.put("content", searchResults.getContent());
                searchData.put("totalElements", searchResults.getTotalElements());
                searchData.put("totalPages", searchResults.getTotalPages());
                searchData.put("pageNumber", searchResults.getNumber());
                searchData.put("pageSize", searchResults.getSize());

                redisTemplate.opsForValue().set(key, searchData, PRODUCT_LIST_TTL, TimeUnit.MINUTES);
                log.debug("✓ Search results cached: key={}, results={}", searchKey, searchResults.getTotalElements());
            } catch (Exception e) {
                log.warn("Failed to cache search results (non-critical): {}", e.getMessage());
            }
        }
    }

    /**
     * Get cached search results
     */
    @SuppressWarnings("unchecked")
    public Page<Product> getCachedSearchResults(String searchKey) {
        if (redisTemplate != null && searchKey != null) {
            try {
                String key = "search:" + searchKey;
                Object cached = redisTemplate.opsForValue().get(key);

                if (cached instanceof Map) {
                    Map<String, Object> searchData = (Map<String, Object>) cached;
                    List<Product> content = (List<Product>) searchData.get("content");
                    Long totalElements = ((Number) searchData.get("totalElements")).longValue();
                    Integer pageNumber = ((Number) searchData.get("pageNumber")).intValue();
                    Integer pageSize = ((Number) searchData.get("pageSize")).intValue();

                    log.debug("✓ Search results cache HIT: key={}, results={}", searchKey, totalElements);
                    return new PageImpl<>(content, org.springframework.data.domain.PageRequest.of(pageNumber, pageSize), totalElements);
                }
            } catch (Exception e) {
                log.warn("Failed to get cached search results (non-critical): {}", e.getMessage());
            }
        }
        log.debug("✗ Search results cache MISS: key={}", searchKey);
        return null;
    }

    /**
     * Cache featured products
     */
    public void cacheFeaturedProducts(Page<Product> products, int page, int size) {
        if (redisTemplate != null && products != null) {
            try {
                String key = "featured:" + page + ":" + size;

                Map<String, Object> data = new HashMap<>();
                data.put("content", products.getContent());
                data.put("totalElements", products.getTotalElements());
                data.put("totalPages", products.getTotalPages());
                data.put("pageNumber", products.getNumber());
                data.put("pageSize", products.getSize());

                redisTemplate.opsForValue().set(key, data, FEATURED_TTL, TimeUnit.MINUTES);
                log.debug("✓ Featured products cached: page={}, size={}", page, size);
            } catch (Exception e) {
                log.warn("Failed to cache featured products (non-critical): {}", e.getMessage());
            }
        }
    }

    /**
     * Get cached featured products
     */
    @SuppressWarnings("unchecked")
    public Page<Product> getCachedFeaturedProducts(int page, int size) {
        if (redisTemplate != null) {
            try {
                String key = "featured:" + page + ":" + size;
                Object cached = redisTemplate.opsForValue().get(key);

                if (cached instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) cached;
                    List<Product> content = (List<Product>) data.get("content");
                    Long totalElements = ((Number) data.get("totalElements")).longValue();
                    Integer pageNumber = ((Number) data.get("pageNumber")).intValue();
                    Integer pageSize = ((Number) data.get("pageSize")).intValue();

                    log.debug("✓ Featured products cache HIT: page={}, size={}", page, size);
                    return new PageImpl<>(content, org.springframework.data.domain.PageRequest.of(pageNumber, pageSize), totalElements);
                }
            } catch (Exception e) {
                log.warn("Failed to get cached featured products (non-critical): {}", e.getMessage());
            }
        }
        log.debug("✗ Featured products cache MISS: page={}, size={}", page, size);
        return null;
    }

    /**
     * Cache best sellers
     */
    public void cacheBestSellers(Page<Product> products, int page, int size) {
        if (redisTemplate != null && products != null) {
            try {
                String key = "bestsellers:" + page + ":" + size;

                Map<String, Object> data = new HashMap<>();
                data.put("content", products.getContent());
                data.put("totalElements", products.getTotalElements());
                data.put("totalPages", products.getTotalPages());
                data.put("pageNumber", products.getNumber());
                data.put("pageSize", products.getSize());

                redisTemplate.opsForValue().set(key, data, BESTSELLERS_TTL, TimeUnit.MINUTES);
                log.debug("✓ Best sellers cached: page={}, size={}", page, size);
            } catch (Exception e) {
                log.warn("Failed to cache best sellers (non-critical): {}", e.getMessage());
            }
        }
    }

    /**
     * Get cached best sellers
     */
    @SuppressWarnings("unchecked")
    public Page<Product> getCachedBestSellers(int page, int size) {
        if (redisTemplate != null) {
            try {
                String key = "bestsellers:" + page + ":" + size;
                Object cached = redisTemplate.opsForValue().get(key);

                if (cached instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) cached;
                    List<Product> content = (List<Product>) data.get("content");
                    Long totalElements = ((Number) data.get("totalElements")).longValue();
                    Integer pageNumber = ((Number) data.get("pageNumber")).intValue();
                    Integer pageSize = ((Number) data.get("pageSize")).intValue();

                    log.debug("✓ Best sellers cache HIT: page={}, size={}", page, size);
                    return new PageImpl<>(content, org.springframework.data.domain.PageRequest.of(pageNumber, pageSize), totalElements);
                }
            } catch (Exception e) {
                log.warn("Failed to get cached best sellers (non-critical): {}", e.getMessage());
            }
        }
        log.debug("✗ Best sellers cache MISS: page={}, size={}", page, size);
        return null;
    }

    /**
     * Cache filter options (brands, sizes, colors)
     */
    public void cacheFilterOptions(String filterType, List<String> options) {
        if (redisTemplate != null && filterType != null && options != null) {
            try {
                String key = "filter:" + filterType;
                redisTemplate.opsForValue().set(key, options, FILTERS_TTL, TimeUnit.MINUTES);
                log.debug("✓ Filter options cached: type={}, count={}", filterType, options.size());
            } catch (Exception e) {
                log.warn("Failed to cache filter options (non-critical): {}", e.getMessage());
            }
        }
    }

    /**
     * Get cached filter options
     */
    @SuppressWarnings("unchecked")
    public List<String> getCachedFilterOptions(String filterType) {
        if (redisTemplate != null && filterType != null) {
            try {
                String key = "filter:" + filterType;
                Object cached = redisTemplate.opsForValue().get(key);
                if (cached instanceof List) {
                    log.debug("✓ Filter options cache HIT: type={}", filterType);
                    return (List<String>) cached;
                }
            } catch (Exception e) {
                log.warn("Failed to get cached filter options (non-critical): {}", e.getMessage());
            }
        }
        log.debug("✗ Filter options cache MISS: type={}", filterType);
        return null;
    }

    /**
     * Invalidate product cache when product is updated/deleted
     */
    public void invalidateProduct(Long productId, String slug) {
        if (redisTemplate != null) {
            try {
                if (productId != null) {
                    String idKey = "product:" + productId;
                    redisTemplate.delete(idKey);
                    log.info("✓ Product cache invalidated: id={}", productId);
                }

                if (slug != null) {
                    String slugKey = "product:slug:" + slug;
                    redisTemplate.delete(slugKey);
                    log.info("✓ Product cache invalidated: slug={}", slug);
                }

                // Clear all search results when product changes
                invalidateAllSearchResults();
            } catch (Exception e) {
                log.warn("Failed to invalidate product cache (non-critical): {}", e.getMessage());
            }
        }
    }

    /**
     * Invalidate all search results (when products change)
     */
    public void invalidateAllSearchResults() {
        if (redisTemplate != null) {
            try {
                // Clear search results
                redisTemplate.delete(redisTemplate.keys("search:*"));

                // Clear featured products
                redisTemplate.delete(redisTemplate.keys("featured:*"));

                // Clear best sellers
                redisTemplate.delete(redisTemplate.keys("bestsellers:*"));

                log.info("✓ All product search caches invalidated");
            } catch (Exception e) {
                log.warn("Failed to invalidate search results (non-critical): {}", e.getMessage());
            }
        }
    }

    /**
     * Generate search key for caching.
     * Prefixed with the current storeId (0 = no tenant) so two storefronts
     * never share cached search results.
     */
    public String generateSearchKey(
            String keyword,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String brand,
            String gender,
            String size,
            String color,
            String sortBy,
            int page,
            int pageSize
    ) {
        Long storeId = com.ut.edu.backend.store.TenantContext.getStoreId();
        return String.format("%s:%s:%s:%s:%s:%s:%s:%s:%s:%s:%d:%d",
                storeId != null ? storeId : 0L,
                keyword != null ? keyword : "",
                categoryId != null ? categoryId : "",
                minPrice != null ? minPrice : "",
                maxPrice != null ? maxPrice : "",
                brand != null ? brand : "",
                gender != null ? gender : "",
                size != null ? size : "",
                color != null ? color : "",
                sortBy != null ? sortBy : "",
                page,
                pageSize
        );
    }
}

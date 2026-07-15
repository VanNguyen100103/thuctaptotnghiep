package com.ut.edu.backend.product;

import com.ut.edu.backend.user.User;
import com.ut.edu.backend.order.OrderItem;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Product entity with custom search queries
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

    Optional<Product> findBySku(String sku);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images WHERE p.active = true")
    Page<Product> findByActiveTrue(Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images WHERE p.featured = true AND p.active = true")
    Page<Product> findByFeaturedTrueAndActiveTrue(Pageable pageable);

    /**
     * User search - search active products only with Vietnamese text support
     * Using unaccent for Vietnamese diacritics (same as admin search but only active products)
     * Native query bypasses the Hibernate tenant filter, so the store scope is
     * passed explicitly; storeId = null searches across all stores (legacy route).
     */
    @Query(value = "SELECT * FROM products p WHERE p.active = true AND " +
           "(CAST(:storeId AS BIGINT) IS NULL OR p.store_id = :storeId) AND " +
           "(unaccent(LOWER(p.name)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "unaccent(LOWER(p.description)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "unaccent(LOWER(p.short_description)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "unaccent(LOWER(p.brand)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%')))",
           countQuery = "SELECT COUNT(*) FROM products p WHERE p.active = true AND " +
           "(CAST(:storeId AS BIGINT) IS NULL OR p.store_id = :storeId) AND " +
           "(unaccent(LOWER(p.name)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "unaccent(LOWER(p.description)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "unaccent(LOWER(p.short_description)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "unaccent(LOWER(p.brand)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%')))",
           nativeQuery = true)
    Page<Product> searchProducts(@Param("keyword") String keyword,
                                 @Param("storeId") Long storeId,
                                 Pageable pageable);

    @Query("SELECT p FROM Product p JOIN p.categories c WHERE c.id = :categoryId AND p.active = true")
    Page<Product> findByCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.price BETWEEN :minPrice AND :maxPrice")
    Page<Product> findByPriceRange(@Param("minPrice") BigDecimal minPrice,
                                   @Param("maxPrice") BigDecimal maxPrice,
                                   Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.gender = :gender")
    Page<Product> findByGender(@Param("gender") String gender, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND LOWER(p.gender) = LOWER(:gender)")
    Page<Product> findByGenderIgnoreCaseAndActiveTrue(@Param("gender") String gender, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.brand = :brand")
    Page<Product> findByBrand(@Param("brand") String brand, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND :size MEMBER OF p.availableSizes")
    Page<Product> findBySize(@Param("size") String size, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND :color MEMBER OF p.availableColors")
    Page<Product> findByColor(@Param("color") String color, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true ORDER BY p.soldCount DESC")
    Page<Product> findBestSellers(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true ORDER BY p.createdAt DESC")
    Page<Product> findNewest(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.compareAtPrice IS NOT NULL " +
           "AND p.compareAtPrice > p.price ORDER BY ((p.compareAtPrice - p.price) / p.compareAtPrice) DESC")
    Page<Product> findOnSale(Pageable pageable);

    @Query("SELECT DISTINCT p.brand FROM Product p WHERE p.active = true AND p.brand IS NOT NULL ORDER BY p.brand")
    List<String> findAllBrands();

    @Query("SELECT DISTINCT size FROM Product p JOIN p.availableSizes size ORDER BY size")
    List<String> findAllSizes();

    @Query("SELECT DISTINCT color FROM Product p JOIN p.availableColors color ORDER BY color")
    List<String> findAllColors();

    Boolean existsBySlug(String slug);

    Boolean existsBySku(String sku);

    /**
     * Find product by ID with pessimistic write lock
     * Used for atomic stock updates to prevent race conditions
     * This will lock the row until transaction commits
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    // ==================== DASHBOARD OPTIMIZATION QUERIES ====================

    /**
     * Count active products
     */
    Long countByActive(Boolean active);

    /**
     * Count products with zero stock
     */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.stockQuantity = 0")
    Long countOutOfStock();

    // ==================== ADMIN SEARCH (ITEM 5) ====================

    /**
     * Store dashboard search - all products of ONE store (including inactive)
     * Using native PostgreSQL query with unaccent for Vietnamese text search
     * Requires: CREATE EXTENSION IF NOT EXISTS unaccent;
     * Native query bypasses the Hibernate tenant filter -> storeId is mandatory.
     */
    @Query(value = "SELECT * FROM products p WHERE p.store_id = :storeId AND " +
           "(unaccent(LOWER(p.name)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "unaccent(LOWER(p.description)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "unaccent(LOWER(p.short_description)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "unaccent(LOWER(p.brand)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%')))",
           countQuery = "SELECT COUNT(*) FROM products p WHERE p.store_id = :storeId AND " +
           "(unaccent(LOWER(p.name)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "unaccent(LOWER(p.description)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "unaccent(LOWER(p.short_description)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "unaccent(LOWER(p.brand)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%') OR " +
           "LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%')))",
           nativeQuery = true)
    Page<Product> adminSearchProducts(@Param("keyword") String keyword,
                                      @Param("storeId") Long storeId,
                                      Pageable pageable);

    // ==================== AI CLUSTERING QUERIES ====================

    /**
     * Calculate total revenue for a product
     * Sum of (quantity * price) from all PAID/COMPLETED/PROCESSING/SHIPPED/DELIVERED orders
     */
    @Query("SELECT COALESCE(SUM(oi.quantity * oi.unitPrice), 0) " +
           "FROM OrderItem oi JOIN oi.order o " +
           "WHERE oi.product = :product AND o.status IN ('PAID', 'COMPLETED', 'PROCESSING', 'SHIPPED', 'DELIVERED')")
    java.math.BigDecimal calculateProductRevenue(@Param("product") Product product);

    /**
     * Calculate total quantity sold for a product
     * From all PAID/COMPLETED/PROCESSING/SHIPPED/DELIVERED orders
     */
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) " +
           "FROM OrderItem oi JOIN oi.order o " +
           "WHERE oi.product = :product AND o.status IN ('PAID', 'COMPLETED', 'PROCESSING', 'SHIPPED', 'DELIVERED')")
    Long calculateProductSalesVolume(@Param("product") Product product);
}

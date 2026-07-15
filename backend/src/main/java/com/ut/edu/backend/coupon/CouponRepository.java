package com.ut.edu.backend.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Coupon entity
 */
@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * Find coupon by code (case-insensitive)
     */
    @Query("SELECT c FROM Coupon c WHERE LOWER(c.code) = LOWER(:code)")
    Optional<Coupon> findByCode(@Param("code") String code);

    /**
     * Find active coupon by code
     */
    @Query("SELECT c FROM Coupon c WHERE LOWER(c.code) = LOWER(:code) AND c.active = true")
    Optional<Coupon> findByCodeAndActiveTrue(@Param("code") String code);

    /**
     * Check if coupon code exists
     */
    boolean existsByCodeIgnoreCase(String code);

    /**
     * Find all active coupons
     */
    Page<Coupon> findByActiveTrue(Pageable pageable);

    /**
     * Find coupons expiring soon
     */
    @Query("SELECT c FROM Coupon c WHERE c.active = true AND c.expiryDate BETWEEN :now AND :futureDate")
    List<Coupon> findExpiringSoon(@Param("now") LocalDateTime now, @Param("futureDate") LocalDateTime futureDate);

    /**
     * Find expired coupons
     */
    @Query("SELECT c FROM Coupon c WHERE c.active = true AND c.expiryDate < :now")
    List<Coupon> findExpired(@Param("now") LocalDateTime now);

    /**
     * Find coupons that have reached usage limit
     */
    @Query("SELECT c FROM Coupon c WHERE c.active = true AND c.maxUsageCount IS NOT NULL AND c.usedCount >= c.maxUsageCount")
    List<Coupon> findReachedUsageLimit();
}

package com.ut.edu.backend.coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for CouponUsage entity
 */
@Repository
public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    /**
     * Count how many times a user has used a specific coupon
     */
    @Query("SELECT COUNT(cu) FROM CouponUsage cu WHERE cu.user.id = :userId AND cu.coupon.id = :couponId")
    int countByUserIdAndCouponId(@Param("userId") Long userId, @Param("couponId") Long couponId);

    /**
     * Find all usages by user
     */
    List<CouponUsage> findByUserId(Long userId);

    /**
     * Find all usages for a specific coupon
     */
    List<CouponUsage> findByCouponId(Long couponId);

    /**
     * Check if user has used this coupon
     */
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
}

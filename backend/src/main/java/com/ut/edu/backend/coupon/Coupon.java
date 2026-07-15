package com.ut.edu.backend.coupon;

import com.ut.edu.backend.order.Order;
import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Coupon entity for discount/promo codes
 * Supports percentage discounts, fixed amount discounts, and free shipping
 */
@Entity
@Table(name = "coupons", indexes = {
    @Index(name = "idx_coupon_code", columnList = "code"),
    @Index(name = "idx_coupon_active", columnList = "active"),
    @Index(name = "idx_coupon_expiry", columnList = "expiryDate"),
    @Index(name = "idx_coupons_store", columnList = "store_id")
}, uniqueConstraints = {
    // coupon code is unique per store, not globally (multi-tenant)
    @UniqueConstraint(name = "uk_coupons_store_code", columnNames = {"store_id", "code"})
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = "store")
public class Coupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tenant link: the store this coupon belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    // Coupon code (e.g., "SUMMER2025", "FREESHIP")
    @NotBlank(message = "Coupon code is required")
    @Column(nullable = false, length = 50)
    private String code;

    // Description
    @NotBlank(message = "Description is required")
    @Column(nullable = false)
    private String description;

    // Discount type
    @NotNull(message = "Discount type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DiscountType discountType;

    // Discount value
    // If PERCENTAGE: 20 = 20%
    // If FIXED_AMOUNT: 50000 = 50,000 VND
    // If FREE_SHIPPING: not used
    @NotNull(message = "Discount value is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    // Minimum order value to apply coupon
    @Column(precision = 10, scale = 2)
    private BigDecimal minimumOrderValue;

    // Maximum discount amount (for PERCENTAGE type)
    @Column(precision = 10, scale = 2)
    private BigDecimal maximumDiscountAmount;

    // Maximum usage count (total)
    @Column
    private Integer maxUsageCount;

    // Current usage count
    @Column(nullable = false)
    @Builder.Default
    private Integer usedCount = 0;

    // Maximum usage per user
    @Column
    private Integer maxUsagePerUser;

    // Start date
    @Column
    private LocalDateTime startDate;

    // Expiry date
    @Column
    private LocalDateTime expiryDate;

    // Active status
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    // Admin notes
    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Check if coupon is valid (not expired, not exceeded usage limit)
     */
    public boolean isValid() {
        if (!active) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        // Check start date
        if (startDate != null && now.isBefore(startDate)) {
            return false;
        }

        // Check expiry date
        if (expiryDate != null && now.isAfter(expiryDate)) {
            return false;
        }

        // Check usage limit
        if (maxUsageCount != null && usedCount >= maxUsageCount) {
            return false;
        }

        return true;
    }

    /**
     * Calculate discount amount for given order subtotal
     *
     * @param orderSubtotal Order subtotal before discount
     * @return Discount amount
     */
    public BigDecimal calculateDiscount(BigDecimal orderSubtotal) {
        if (!isValid()) {
            return BigDecimal.ZERO;
        }

        // Check minimum order value
        if (minimumOrderValue != null && orderSubtotal.compareTo(minimumOrderValue) < 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount;

        switch (discountType) {
            case PERCENTAGE:
                // Calculate percentage discount
                discount = orderSubtotal
                        .multiply(discountValue)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                // Apply maximum cap
                if (maximumDiscountAmount != null && discount.compareTo(maximumDiscountAmount) > 0) {
                    discount = maximumDiscountAmount;
                }
                break;

            case FIXED_AMOUNT:
                discount = discountValue;

                // Cannot exceed subtotal
                if (discount.compareTo(orderSubtotal) > 0) {
                    discount = orderSubtotal;
                }
                break;

            case FREE_SHIPPING:
                // Free shipping is handled separately in order calculation
                discount = BigDecimal.ZERO;
                break;

            default:
                discount = BigDecimal.ZERO;
        }

        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Increment usage count
     */
    public void incrementUsedCount() {
        this.usedCount++;
    }

    /**
     * Check if coupon can be used (has remaining usage)
     */
    public boolean hasRemainingUsage() {
        if (maxUsageCount == null) {
            return true;  // Unlimited usage
        }
        return usedCount < maxUsageCount;
    }
}

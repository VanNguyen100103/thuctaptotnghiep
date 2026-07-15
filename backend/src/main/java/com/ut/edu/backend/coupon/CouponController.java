package com.ut.edu.backend.coupon;

import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.store.TenantGuard;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Coupon Controller
 * Handles coupon management and validation
 */
@RestController
@RequestMapping("/coupons")
@Slf4j
public class CouponController {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponUsageRepository couponUsageRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private TenantGuard tenantGuard;

    /**
     * Load a coupon only if it belongs to the current store; cross-tenant
     * ids look like "not found" (anti-IDOR). Used by owner/manager endpoints.
     */
    private Coupon findStoreCoupon(Long id) {
        return couponRepository.findById(id)
                .filter(c -> tenantGuard.isCurrentStore(c.getStore()))
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found: " + id));
    }

    /**
     * Validate coupon code
     * GET /api/coupons/validate?code=SUMMER2025&orderSubtotal=278000
     */
    @GetMapping("/validate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> validateCoupon(
            @RequestParam String code,
            @RequestParam BigDecimal orderSubtotal) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();

            // Find coupon
            Coupon coupon = couponRepository.findByCodeAndActiveTrue(code)
                    .orElse(null);

            if (coupon == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "valid", false,
                                "message", "Invalid coupon code"
                        ));
            }

            // Check if valid
            if (!coupon.isValid()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "valid", false,
                                "message", "Coupon has expired or reached usage limit"
                        ));
            }

            // Check minimum order value
            if (coupon.getMinimumOrderValue() != null &&
                orderSubtotal.compareTo(coupon.getMinimumOrderValue()) < 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "valid", false,
                                "message", String.format("Minimum order value is %.2f", coupon.getMinimumOrderValue())
                        ));
            }

            // Check per-user usage limit
            if (coupon.getMaxUsagePerUser() != null) {
                int userUsageCount = couponUsageRepository.countByUserIdAndCouponId(
                        currentUserId,
                        coupon.getId()
                );

                if (userUsageCount >= coupon.getMaxUsagePerUser()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of(
                                    "valid", false,
                                    "message", "You have already used this coupon"
                            ));
                }
            }

            // Calculate discount
            BigDecimal discountAmount = coupon.calculateDiscount(orderSubtotal);

            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("code", coupon.getCode());
            response.put("description", coupon.getDescription());
            response.put("discountType", coupon.getDiscountType());
            response.put("discountValue", coupon.getDiscountValue());
            response.put("discountAmount", discountAmount);
            response.put("freeShipping", coupon.getDiscountType() == DiscountType.FREE_SHIPPING);
            response.put("message", "Coupon is valid");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to validate coupon", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to validate coupon"));
        }
    }

    /**
     * Get current user's coupon usage history
     * GET /api/coupons/my-usage
     */
    @GetMapping("/my-usage")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyUsage() {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();

            // Get all coupon usages for this user
            List<CouponUsage> usages = couponUsageRepository.findByUserId(currentUserId);

            // Build response with detailed information
            List<Map<String, Object>> usageDetails = usages.stream()
                .map(usage -> {
                    Coupon coupon = usage.getCoupon();

                    // Count how many times user has used this coupon
                    int timesUsed = couponUsageRepository.countByUserIdAndCouponId(
                        currentUserId,
                        coupon.getId()
                    );

                    // Check if can use again
                    boolean canUseAgain = coupon.getMaxUsagePerUser() == null ||
                                         timesUsed < coupon.getMaxUsagePerUser();

                    Map<String, Object> detail = new HashMap<>();
                    detail.put("code", coupon.getCode());
                    detail.put("description", coupon.getDescription());
                    detail.put("discountType", coupon.getDiscountType());
                    detail.put("discountValue", coupon.getDiscountValue());
                    detail.put("usedAt", usage.getUsedAt());
                    detail.put("orderNumber", usage.getOrder().getOrderNumber());
                    detail.put("orderId", usage.getOrder().getId());
                    detail.put("canUseAgain", canUseAgain);
                    detail.put("maxUsagePerUser", coupon.getMaxUsagePerUser());
                    detail.put("timesUsed", timesUsed);

                    return detail;
                })
                .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("usedCoupons", usageDetails);
            response.put("totalUsed", usages.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get coupon usage history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve coupon usage history"));
        }
    }

    /**
     * Get all coupons (Admin only)
     * GET /api/coupons?page=0&size=10
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<?> getAllCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Boolean active) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

            Page<Coupon> couponsPage;
            if (active != null && active) {
                couponsPage = couponRepository.findByActiveTrue(pageable);
            } else {
                couponsPage = couponRepository.findAll(pageable);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("coupons", couponsPage.getContent());
            response.put("currentPage", couponsPage.getNumber());
            response.put("totalPages", couponsPage.getTotalPages());
            response.put("totalCoupons", couponsPage.getTotalElements());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get coupons", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve coupons"));
        }
    }

    /**
     * Get coupon by ID (Admin only)
     * GET /api/coupons/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<?> getCouponById(@PathVariable Long id) {
        try {
            Coupon coupon = findStoreCoupon(id);

            return ResponseEntity.ok(coupon);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get coupon: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve coupon"));
        }
    }

    /**
     * Create coupon (Admin only)
     * POST /api/coupons
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<?> createCoupon(@Valid @RequestBody CreateCouponRequest request) {
        try {
            // Check if code already exists
            if (couponRepository.existsByCodeIgnoreCase(request.getCode())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Coupon code already exists"));
            }

            Coupon coupon = Coupon.builder()
                    .store(tenantGuard.currentStoreRef())
                    .code(request.getCode().toUpperCase())
                    .description(request.getDescription())
                    .discountType(request.getDiscountType())
                    .discountValue(request.getDiscountValue())
                    .minimumOrderValue(request.getMinimumOrderValue())
                    .maximumDiscountAmount(request.getMaximumDiscountAmount())
                    .maxUsageCount(request.getMaxUsageCount())
                    .maxUsagePerUser(request.getMaxUsagePerUser())
                    .startDate(request.getStartDate())
                    .expiryDate(request.getExpiryDate())
                    .active(true)
                    .usedCount(0)
                    .notes(request.getNotes())
                    .build();

            coupon = couponRepository.save(coupon);

            log.info("Coupon created: {}", coupon.getCode());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Coupon created successfully",
                            "coupon", coupon
                    ));

        } catch (Exception e) {
            log.error("Failed to create coupon", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create coupon: " + e.getMessage()));
        }
    }

    /**
     * Update coupon (Admin only)
     * PUT /api/coupons/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<?> updateCoupon(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCouponRequest request) {
        try {
            Coupon coupon = findStoreCoupon(id);

            // Update fields
            if (request.getDescription() != null) {
                coupon.setDescription(request.getDescription());
            }
            if (request.getDiscountValue() != null) {
                coupon.setDiscountValue(request.getDiscountValue());
            }
            if (request.getMinimumOrderValue() != null) {
                coupon.setMinimumOrderValue(request.getMinimumOrderValue());
            }
            if (request.getMaximumDiscountAmount() != null) {
                coupon.setMaximumDiscountAmount(request.getMaximumDiscountAmount());
            }
            if (request.getMaxUsageCount() != null) {
                coupon.setMaxUsageCount(request.getMaxUsageCount());
            }
            if (request.getMaxUsagePerUser() != null) {
                coupon.setMaxUsagePerUser(request.getMaxUsagePerUser());
            }
            if (request.getStartDate() != null) {
                coupon.setStartDate(request.getStartDate());
            }
            if (request.getExpiryDate() != null) {
                coupon.setExpiryDate(request.getExpiryDate());
            }
            if (request.getActive() != null) {
                coupon.setActive(request.getActive());
            }
            if (request.getNotes() != null) {
                coupon.setNotes(request.getNotes());
            }
            if (request.getUsedCount() != null) {
                coupon.setUsedCount(request.getUsedCount());
            }

            coupon = couponRepository.save(coupon);

            log.info("Coupon updated: {}", coupon.getCode());

            return ResponseEntity.ok(Map.of(
                    "message", "Coupon updated successfully",
                    "coupon", coupon
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update coupon: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update coupon"));
        }
    }

    /**
     * Delete coupon (Admin only)
     * DELETE /api/coupons/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<?> deleteCoupon(@PathVariable Long id) {
        try {
            Coupon coupon = findStoreCoupon(id);

            // Soft delete: just deactivate
            coupon.setActive(false);
            couponRepository.save(coupon);

            log.info("Coupon deleted (deactivated): {}", coupon.getCode());

            return ResponseEntity.ok(Map.of(
                    "message", "Coupon deleted successfully"
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to delete coupon: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete coupon"));
        }
    }

    // ==================== REQUEST DTOs ====================

    @lombok.Data
    public static class CreateCouponRequest {
        @jakarta.validation.constraints.NotBlank(message = "Coupon code is required")
        private String code;

        @jakarta.validation.constraints.NotBlank(message = "Description is required")
        private String description;

        @jakarta.validation.constraints.NotNull(message = "Discount type is required")
        private DiscountType discountType;

        @jakarta.validation.constraints.NotNull(message = "Discount value is required")
        private BigDecimal discountValue;

        private BigDecimal minimumOrderValue;
        private BigDecimal maximumDiscountAmount;
        private Integer maxUsageCount;
        private Integer maxUsagePerUser;
        private LocalDateTime startDate;
        private LocalDateTime expiryDate;
        private String notes;
    }

    @lombok.Data
    public static class UpdateCouponRequest {
        private String description;
        private BigDecimal discountValue;
        private BigDecimal minimumOrderValue;
        private BigDecimal maximumDiscountAmount;
        private Integer maxUsageCount;
        private Integer maxUsagePerUser;
        private Integer usedCount;  // Allow admin to reset usage count
        private LocalDateTime startDate;
        private LocalDateTime expiryDate;
        private Boolean active;
        private String notes;
    }
}

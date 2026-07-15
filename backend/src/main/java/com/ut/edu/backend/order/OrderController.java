package com.ut.edu.backend.order;

import com.ut.edu.backend.user.UserRepository;
import com.ut.edu.backend.user.AddressRepository;
import com.ut.edu.backend.user.User;
import com.ut.edu.backend.user.Address;
import com.ut.edu.backend.product.ProductRepository;
import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.product.ProductImage;
import com.ut.edu.backend.cart.CartRepository;
import com.ut.edu.backend.cart.CartItemRepository;
import com.ut.edu.backend.cart.Cart;
import com.ut.edu.backend.cart.CartItem;
import com.ut.edu.backend.payment.PaymentController;
import com.ut.edu.backend.payment.Payment;
import com.ut.edu.backend.coupon.CouponRepository;
import com.ut.edu.backend.coupon.CouponUsageRepository;
import com.ut.edu.backend.coupon.Coupon;
import com.ut.edu.backend.coupon.CouponUsage;
import com.ut.edu.backend.store.Store;

import com.ut.edu.backend.user.AddressType;
import com.ut.edu.backend.coupon.DiscountType;
import com.ut.edu.backend.security.AuthorizationService;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Order Controller
 * Handles order creation, checkout, and order management
 */
@RestController
@RequestMapping("/orders")
@Slf4j
@Transactional
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponUsageRepository couponUsageRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private AddressRepository addressRepository;

    /**
     * Get current user's orders
     * GET /api/orders?page=0&size=10
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getUserOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();

            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Order> ordersPage = orderRepository.findByUserId(currentUserId, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("orders", ordersPage.getContent().stream()
                    .map(this::buildOrderSummaryResponse)
                    .collect(Collectors.toList()));
            response.put("currentPage", ordersPage.getNumber());
            response.put("totalPages", ordersPage.getTotalPages());
            response.put("totalOrders", ordersPage.getTotalElements());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get user orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve orders"));
        }
    }

    /**
     * Get order details by ID (with IDOR protection)
     * GET /api/orders/{orderId}
     */
    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getOrderById(@PathVariable Long orderId) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

            // IDOR Protection: Check ownership
            if (!order.getUser().getId().equals(currentUserId) &&
                !authorizationService.isAdmin()) {
                log.warn("IDOR attempt: User {} tried to access order {} owned by user {}",
                        currentUserId, orderId, order.getUser().getId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
            }

            return ResponseEntity.ok(buildOrderDetailResponse(order));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get order: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve order"));
        }
    }

    /**
     * Create order from cart (Checkout)
     * POST /api/orders/checkout
     *
     * Request body:
     * {
     *   "shippingAddress": {
     *     "addressLine1": "123 Main St",
     *     "addressLine2": "Apt 4B",
     *     "city": "New York",
     *     "stateProvince": "NY",
     *     "postalCode": "10001",
     *     "country": "USA",
     *     "phoneNumber": "+1234567890"
     *   },
     *   "email": "user@example.com"
     * }
     */
    @PostMapping("/checkout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> checkout(@Valid @RequestBody CheckoutRequest request) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();

            // Get user's cart
            Cart cart = cartRepository.findByUserId(currentUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

            if (cart.getItems().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Cart is empty"));
            }

            // Validate stock availability for all items
            for (CartItem cartItem : cart.getItems()) {
                Product product = cartItem.getProduct();

                if (!product.getActive()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Product '" + product.getName() + "' is no longer available"));
                }

                if (product.getStockQuantity() < cartItem.getQuantity()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Insufficient stock for '" + product.getName() +
                                    "'. Only " + product.getStockQuantity() + " items available"));
                }
            }

            // Calculate totals
            BigDecimal subtotal = cart.getTotalPrice();
            BigDecimal shippingCost = calculateShippingCost(subtotal);
            BigDecimal taxAmount = calculateTax(subtotal);
            BigDecimal discountAmount = BigDecimal.ZERO;

            // Apply coupon if provided
            Coupon appliedCoupon = null;
            boolean freeShipping = false;

            if (request.getCouponCode() != null && !request.getCouponCode().trim().isEmpty()) {
                appliedCoupon = couponRepository.findByCodeAndActiveTrue(request.getCouponCode())
                        .orElse(null);

                if (appliedCoupon == null) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Invalid coupon code"));
                }

                if (!appliedCoupon.isValid()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Coupon has expired or reached usage limit"));
                }

                // Check per-user usage limit
                if (appliedCoupon.getMaxUsagePerUser() != null) {
                    int userUsageCount = couponUsageRepository.countByUserIdAndCouponId(
                            currentUserId,
                            appliedCoupon.getId()
                    );

                    if (userUsageCount >= appliedCoupon.getMaxUsagePerUser()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "You have already used this coupon"));
                    }
                }

                // Apply discount
                if (appliedCoupon.getDiscountType() == DiscountType.FREE_SHIPPING) {
                    freeShipping = true;
                    shippingCost = BigDecimal.ZERO;
                } else {
                    discountAmount = appliedCoupon.calculateDiscount(subtotal);

                    if (discountAmount.compareTo(BigDecimal.ZERO) == 0) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error",
                                        String.format("Order subtotal does not meet minimum requirement: %.2f",
                                                appliedCoupon.getMinimumOrderValue())));
                    }
                }
            }

            BigDecimal total = subtotal.add(shippingCost).add(taxAmount).subtract(discountAmount);

            // Generate unique order number
            String orderNumber = generateOrderNumber();

            // Get user
            User user = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // Tenant link: the order belongs to the store the cart was filled
            // in; legacy carts without a store fall back to the items' store
            Store orderStore = cart.getStore();
            if (orderStore == null) {
                orderStore = cart.getItems().stream()
                        .map(item -> item.getProduct().getStore())
                        .filter(s -> s != null)
                        .findFirst()
                        .orElse(null);
            }

            // Create order with PENDING status
            // NOTE: Stock will NOT be decreased until payment is confirmed
            // This prevents stock being locked when users don't complete payment
            Order order = Order.builder()
                    .orderNumber(orderNumber)
                    .store(orderStore)
                    .user(user)
                    .status(OrderStatus.PENDING)
                    .subtotal(subtotal)
                    .shippingCost(shippingCost)
                    .taxAmount(taxAmount)
                    .discountAmount(discountAmount)
                    .total(total)
                    .coupon(appliedCoupon)
                    .couponCode(appliedCoupon != null ? appliedCoupon.getCode() : null)
                    .shippingAddressLine1(request.getShippingAddress().getAddressLine1())
                    .shippingAddressLine2(request.getShippingAddress().getAddressLine2())
                    .shippingCity(request.getShippingAddress().getCity())
                    .shippingStateProvince(request.getShippingAddress().getStateProvince())
                    .shippingPostalCode(request.getShippingAddress().getPostalCode())
                    .shippingCountry(request.getShippingAddress().getCountry())
                    .shippingPhoneNumber(request.getShippingAddress().getPhoneNumber())
                    .shippingEmail(request.getEmail())
                    .notes(appliedCoupon != null ?
                            String.format("Coupon applied: %s. Discount: %.2f. Stock not yet reserved.",
                                    appliedCoupon.getCode(), discountAmount) :
                            "Order pending payment. Stock not yet reserved.")
                    .build();

            order = orderRepository.save(order);

            // Create order items from cart items (with size and color)
            // Stock quantities are saved but NOT decremented
            for (CartItem cartItem : cart.getItems()) {
                Product product = cartItem.getProduct();

                // Get product image URL
                String imageUrl = product.getImages().stream()
                        .findFirst()
                        .map(ProductImage::getImageUrl)
                        .orElse(null);

                // Create order item with size and color
                OrderItem orderItem = OrderItem.builder()
                        .order(order)
                        .product(product)
                        .productName(product.getName())
                        .productSku(product.getSku())
                        .productImageUrl(imageUrl)
                        .quantity(cartItem.getQuantity())
                        .selectedSize(cartItem.getSelectedSize())  // Save selected size
                        .selectedColor(cartItem.getSelectedColor()) // Save selected color
                        .unitPrice(cartItem.getPriceAtAdd())
                        .subtotal(cartItem.getSubtotal())
                        .discountAmount(BigDecimal.ZERO)
                        .build();

                order.getItems().add(orderItem);

                // ✅ REMOVED: Stock decrease moved to PaymentController.executePayment()
                // This prevents stock being locked for unpaid orders
            }

            order = orderRepository.save(order);

            // Track coupon usage
            if (appliedCoupon != null) {
                appliedCoupon.incrementUsedCount();
                couponRepository.save(appliedCoupon);

                // Create coupon usage record (same store as the order)
                CouponUsage couponUsage = CouponUsage.builder()
                        .store(order.getStore())
                        .user(user)
                        .coupon(appliedCoupon)
                        .order(order)
                        .usedAt(LocalDateTime.now())
                        .build();
                couponUsageRepository.save(couponUsage);

                log.info("Coupon {} applied to order {}", appliedCoupon.getCode(), orderNumber);
            }

            // Auto-save shipping address to addresses table for future use
            saveShippingAddressIfNotExists(user, request.getShippingAddress());

            // Clear cart after successful order creation and flush immediately
            cartItemRepository.deleteByCartId(cart.getId());
            cartItemRepository.flush();

            log.info("Order created successfully: {} for user: {}", orderNumber, currentUserId);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Order created successfully",
                            "order", buildOrderDetailResponse(order),
                            "couponApplied", appliedCoupon != null,
                            "discountAmount", discountAmount
                    ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to create order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create order: " + e.getMessage()));
        }
    }

    /**
     * Cancel order (only if status is PENDING)
     * PUT /api/orders/{orderId}/cancel
     */
    @PutMapping("/{orderId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> cancelOrder(@PathVariable Long orderId) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

            // IDOR Protection
            if (!order.getUser().getId().equals(currentUserId)) {
                log.warn("IDOR attempt: User {} tried to cancel order {} owned by user {}",
                        currentUserId, orderId, order.getUser().getId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
            }

            // Only allow cancellation of PENDING orders
            if (order.getStatus() != OrderStatus.PENDING) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Only pending orders can be cancelled"));
            }

            // ✅ NO NEED to restore stock for PENDING orders
            // Stock was never decreased during checkout, only during payment execution
            // If order was paid, it would be PROCESSING status, not PENDING

            order.setStatus(OrderStatus.CANCELLED);
            order.setAdminNotes("Cancelled by user. No stock adjustment needed (payment was not completed).");
            order = orderRepository.save(order);

            log.info("Order cancelled: {} by user: {}", order.getOrderNumber(), currentUserId);

            return ResponseEntity.ok(Map.of(
                    "message", "Order cancelled successfully",
                    "order", buildOrderDetailResponse(order)
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to cancel order: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to cancel order"));
        }
    }

    /**
     * Get order tracking info
     * GET /api/orders/{orderId}/track
     */
    @GetMapping("/{orderId}/track")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> trackOrder(@PathVariable Long orderId) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

            // IDOR Protection
            if (!order.getUser().getId().equals(currentUserId) &&
                !authorizationService.isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
            }

            Map<String, Object> tracking = new HashMap<>();
            tracking.put("orderNumber", order.getOrderNumber());
            tracking.put("status", order.getStatus());
            tracking.put("trackingNumber", order.getTrackingNumber());
            tracking.put("createdAt", order.getCreatedAt());
            tracking.put("updatedAt", order.getUpdatedAt());

            return ResponseEntity.ok(tracking);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to track order: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to track order"));
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Generate unique order number
     */
    private String generateOrderNumber() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = String.valueOf((int) (Math.random() * 10000));
        return "ORD-" + timestamp + "-" + random;
    }

    /**
     * Calculate shipping cost (simple logic - can be enhanced)
     */
    private BigDecimal calculateShippingCost(BigDecimal subtotal) {
        // Free shipping for orders above $100
        if (subtotal.compareTo(new BigDecimal("100")) >= 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal("10.00");
    }

    /**
     * Calculate tax (10% - can be customized based on region)
     */
    private BigDecimal calculateTax(BigDecimal subtotal) {
        return subtotal.multiply(new BigDecimal("0.10"));
    }

    /**
     * Build order summary response (for list view)
     */
    private Map<String, Object> buildOrderSummaryResponse(Order order) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", order.getId());
        response.put("orderNumber", order.getOrderNumber());
        response.put("status", order.getStatus());
        response.put("total", order.getTotal());
        response.put("itemCount", order.getItems().size());
        response.put("createdAt", order.getCreatedAt());

        return response;
    }

    /**
     * Build order detail response (for detail view)
     */
    private Map<String, Object> buildOrderDetailResponse(Order order) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", order.getId());
        response.put("orderNumber", order.getOrderNumber());
        response.put("status", order.getStatus());
        response.put("subtotal", order.getSubtotal());
        response.put("shippingCost", order.getShippingCost());
        response.put("taxAmount", order.getTaxAmount());
        response.put("discountAmount", order.getDiscountAmount());
        response.put("total", order.getTotal());

        // Shipping address
        Map<String, Object> shippingAddress = new HashMap<>();
        shippingAddress.put("addressLine1", order.getShippingAddressLine1());
        shippingAddress.put("addressLine2", order.getShippingAddressLine2());
        shippingAddress.put("city", order.getShippingCity());
        shippingAddress.put("stateProvince", order.getShippingStateProvince());
        shippingAddress.put("postalCode", order.getShippingPostalCode());
        shippingAddress.put("country", order.getShippingCountry());
        shippingAddress.put("phoneNumber", order.getShippingPhoneNumber());
        response.put("shippingAddress", shippingAddress);

        response.put("shippingEmail", order.getShippingEmail());
        response.put("trackingNumber", order.getTrackingNumber());

        // Order items with size and color
        List<Map<String, Object>> items = order.getItems().stream()
                .map(item -> {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("id", item.getId());
                    itemMap.put("productId", item.getProduct().getId());
                    itemMap.put("productName", item.getProductName());
                    itemMap.put("productSku", item.getProductSku());
                    itemMap.put("productImage", item.getProductImageUrl());
                    itemMap.put("quantity", item.getQuantity());
                    itemMap.put("size", item.getSelectedSize());          // Include size
                    itemMap.put("color", item.getSelectedColor());        // Include color
                    itemMap.put("unitPrice", item.getUnitPrice());
                    itemMap.put("subtotal", item.getSubtotal());
                    itemMap.put("discountAmount", item.getDiscountAmount());
                    return itemMap;
                })
                .collect(Collectors.toList());
        response.put("items", items);

        response.put("createdAt", order.getCreatedAt());
        response.put("updatedAt", order.getUpdatedAt());

        return response;
    }

    /**
     * Auto-save shipping address to addresses table if not exists
     * This allows users to reuse addresses in future orders
     */
    private void saveShippingAddressIfNotExists(User user, ShippingAddressDTO shippingAddress) {
        try {
            // Check if this exact address already exists for the user
            List<Address> existingAddresses = addressRepository.findByUserId(user.getId());

            boolean addressExists = existingAddresses.stream().anyMatch(addr ->
                addr.getAddressLine1().equalsIgnoreCase(shippingAddress.getAddressLine1()) &&
                addr.getCity().equalsIgnoreCase(shippingAddress.getCity()) &&
                addr.getPostalCode().equalsIgnoreCase(shippingAddress.getPostalCode()) &&
                addr.getCountry().equalsIgnoreCase(shippingAddress.getCountry()) &&
                addr.getType() == AddressType.SHIPPING
            );

            // Only save if address doesn't exist
            if (!addressExists) {
                // Check if this is the first address for this user
                boolean isFirstAddress = existingAddresses.isEmpty() ||
                    existingAddresses.stream().noneMatch(addr -> addr.getType() == AddressType.SHIPPING);

                Address newAddress = Address.builder()
                    .user(user)
                    .addressLine1(shippingAddress.getAddressLine1())
                    .addressLine2(shippingAddress.getAddressLine2())
                    .city(shippingAddress.getCity())
                    .stateProvince(shippingAddress.getStateProvince())
                    .postalCode(shippingAddress.getPostalCode())
                    .country(shippingAddress.getCountry())
                    .type(AddressType.SHIPPING)
                    .isDefault(isFirstAddress)  // Set as default if it's the first shipping address
                    .build();

                addressRepository.save(newAddress);
                log.info("Auto-saved new shipping address for user: {} (default: {})",
                    user.getId(), isFirstAddress);
            } else {
                log.debug("Shipping address already exists for user: {}, skipping save", user.getId());
            }
        } catch (Exception e) {
            // Don't fail the checkout if address saving fails
            log.error("Failed to auto-save shipping address for user: {}", user.getId(), e);
        }
    }

    // ==================== REQUEST DTOs ====================

    /**
     * Checkout request DTO
     */
    @lombok.Data
    public static class CheckoutRequest {
        @Valid
        @jakarta.validation.constraints.NotNull(message = "Shipping address is required")
        private ShippingAddressDTO shippingAddress;

        @jakarta.validation.constraints.NotBlank(message = "Email is required")
        @jakarta.validation.constraints.Email(message = "Invalid email format")
        private String email;

        // Payment method (optional - if not provided, order created without immediate payment)
        // Options: "PAYPAL", "CASH_ON_DELIVERY", "BANK_TRANSFER", etc.
        private String paymentMethod;

        // Coupon code (optional)
        private String couponCode;
    }

    /**
     * Shipping address DTO
     */
    @lombok.Data
    public static class ShippingAddressDTO {
        @jakarta.validation.constraints.NotBlank(message = "Address line 1 is required")
        private String addressLine1;

        private String addressLine2;

        @jakarta.validation.constraints.NotBlank(message = "City is required")
        private String city;

        @jakarta.validation.constraints.NotBlank(message = "State/Province is required")
        private String stateProvince;

        @jakarta.validation.constraints.NotBlank(message = "Postal code is required")
        private String postalCode;

        @jakarta.validation.constraints.NotBlank(message = "Country is required")
        private String country;

        private String phoneNumber;
    }
}

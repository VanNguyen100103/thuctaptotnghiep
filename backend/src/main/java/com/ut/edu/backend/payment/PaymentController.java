package com.ut.edu.backend.payment;

import com.ut.edu.backend.user.User;
import com.ut.edu.backend.cart.Cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypal.api.payments.Payment;
import com.paypal.base.rest.PayPalRESTException;
import com.ut.edu.backend.order.OrderStatus;
import com.ut.edu.backend.order.Order;
import com.ut.edu.backend.order.OrderItem;
import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.cart.CartItemRepository;
import com.ut.edu.backend.cart.CartRepository;
import com.ut.edu.backend.order.OrderRepository;
import com.ut.edu.backend.product.ProductRepository;
import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.cart.RedisCartCacheService;
import com.ut.edu.backend.email.EmailService;
import com.ut.edu.backend.store.TenantGuard;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;

import java.util.Map;

/**
 * Payment Controller
 * Handles PayPal payment creation, execution, and webhooks
 */
@RestController
@RequestMapping("/payments")
@Slf4j
@Transactional
public class PaymentController {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private RedisCartCacheService redisCartCacheService;

    @Autowired
    private PayPalService payPalService;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmailService emailService;

    @Autowired
    private TenantGuard tenantGuard;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Create PayPal payment for an order
     * POST /api/payments/create
     *
     * Request body:
     * {
     *   "orderId": 1
     * }
     */
    @PostMapping("/create")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createPayment(@RequestBody Map<String, Long> request) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();
            Long orderId = request.get("orderId");

            if (orderId == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Order ID is required"));
            }

            // Get order
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

            // IDOR Protection: Verify ownership
            if (!order.getUser().getId().equals(currentUserId)) {
                log.warn("IDOR attempt: User {} tried to create payment for order {} owned by user {}",
                        currentUserId, orderId, order.getUser().getId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
            }

            // Check if order already has a payment
            if (paymentRepository.findByOrderId(orderId).isPresent()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Payment already exists for this order"));
            }

            // Validate order status
            if (order.getStatus() != OrderStatus.PENDING) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Only pending orders can be paid"));
            }

            // Create PayPal payment
            String successUrl = frontendUrl + "/payment/success";
            String cancelUrl = frontendUrl + "/payment/cancel";

            Payment paypalPayment = payPalService.createPayment(order, successUrl, cancelUrl);

            // Get approval URL
            String approvalUrl = payPalService.getApprovalUrl(paypalPayment);
            if (approvalUrl == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to get PayPal approval URL"));
            }

            // Create payment record in database
            com.ut.edu.backend.payment.Payment payment = com.ut.edu.backend.payment.Payment.builder()
                    .order(order)
                    .store(order.getStore()) // tenant link: payment belongs to the order's store
                    .paymentMethod(PaymentMethod.PAYPAL)
                    .status(PaymentStatus.PENDING)
                    .amount(order.getTotal())
                    .currency("USD")
                    .paypalOrderId(paypalPayment.getId())
                    .paymentDetails(objectMapper.writeValueAsString(paypalPayment))
                    .build();

            payment = paymentRepository.save(payment);

            // Update order status to PAYMENT_PENDING
            order.setStatus(OrderStatus.PAYMENT_PENDING);
            order.setNotes("Payment initiated. Awaiting PayPal approval.");
            orderRepository.save(order);

            log.info("PayPal payment created successfully for order: {} with payment ID: {}",
                    order.getOrderNumber(), paypalPayment.getId());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Payment created successfully",
                            "paymentId", payment.getId(),
                            "paypalOrderId", paypalPayment.getId(),
                            "approvalUrl", approvalUrl,
                            "status", payment.getStatus()
                    ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (PayPalRESTException e) {
            log.error("PayPal API error while creating payment", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "PayPal service error: " + e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to create payment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create payment: " + e.getMessage()));
        }
    }

    /**
     * Execute/Capture PayPal payment after user approval
     * POST /api/payments/execute
     *
     * Request body:
     * {
     *   "paymentId": "PAYID-...",
     *   "payerId": "PAYER-ID-..."
     * }
     */
    @PostMapping("/execute")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> executePayment(@RequestBody Map<String, String> request) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();
            String paypalPaymentId = request.get("paymentId");
            String payerId = request.get("payerId");

            if (paypalPaymentId == null || payerId == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Payment ID and Payer ID are required"));
            }

            // Find payment record
            com.ut.edu.backend.payment.Payment payment = paymentRepository.findByPaypalOrderId(paypalPaymentId)
                    .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paypalPaymentId));

            // IDOR Protection: Verify ownership
            if (!payment.getOrder().getUser().getId().equals(currentUserId)) {
                log.warn("IDOR attempt: User {} tried to execute payment {} owned by user {}",
                        currentUserId, payment.getId(), payment.getOrder().getUser().getId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
            }

            // Check payment status
            if (payment.getStatus() != PaymentStatus.PENDING) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Payment already processed with status: " + payment.getStatus()));
            }

            // Execute PayPal payment
            Payment executedPayment = payPalService.executePayment(paypalPaymentId, payerId);

            // Check if payment was successful
            if ("approved".equalsIgnoreCase(executedPayment.getState())) {
                // Get transaction ID (sale ID)
                String saleId = payPalService.getSaleId(executedPayment);

                // ✅ ATOMIC STOCK DECREASE WITH PESSIMISTIC LOCKING
                // Re-validate stock availability and decrease atomically
                Order order = payment.getOrder();

                try {
                    for (OrderItem orderItem : order.getItems()) {
                        // Use pessimistic write lock to prevent race conditions
                        Product product = productRepository.findByIdWithLock(orderItem.getProduct().getId())
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Product not found: " + orderItem.getProduct().getId()));

                        // Re-validate stock (could have changed since checkout)
                        if (!product.getActive()) {
                            throw new IllegalStateException(
                                    "Product '" + product.getName() + "' is no longer available");
                        }

                        if (product.getStockQuantity() < orderItem.getQuantity()) {
                            throw new IllegalStateException(
                                    "Insufficient stock for '" + product.getName() + "'. " +
                                    "Available: " + product.getStockQuantity() + ", " +
                                    "Required: " + orderItem.getQuantity());
                        }

                        // Atomically decrease stock
                        product.decrementStock(orderItem.getQuantity());
                        product.incrementSoldCount(orderItem.getQuantity());
                        productRepository.save(product);

                        log.debug("Stock decreased for product: {} (ID: {}), Quantity: {}, Remaining: {}",
                                product.getName(), product.getId(), orderItem.getQuantity(), product.getStockQuantity());
                    }
                } catch (IllegalStateException e) {
                    // Stock validation failed - need to refund payment
                    log.error("Stock validation failed after payment: {}", e.getMessage());

                    // Mark payment as failed
                    payment.markAsFailed("Stock validation failed: " + e.getMessage());
                    payment.setPaymentDetails(objectMapper.writeValueAsString(executedPayment));
                    paymentRepository.save(payment);

                    // Mark order as failed
                    order.setStatus(OrderStatus.FAILED);
                    order.setAdminNotes("Payment succeeded but stock unavailable. Requires manual refund: " + e.getMessage());
                    orderRepository.save(order);

                 
                    // For now, admin must manually refund

                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of(
                                    "error", "Payment succeeded but stock is no longer available: " + e.getMessage(),
                                    "requiresRefund", true,
                                    "orderId", order.getId(),
                                    "paymentId", payment.getId()
                            ));
                }

                // Update payment record
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setTransactionId(saleId);
                payment.setPaypalPayerId(payerId);
                payment.setPaymentDate(LocalDateTime.now());
                payment.setPaymentDetails(objectMapper.writeValueAsString(executedPayment));

                // Get payer email if available
                if (executedPayment.getPayer() != null &&
                    executedPayment.getPayer().getPayerInfo() != null) {
                    payment.setPaypalPayerEmail(executedPayment.getPayer().getPayerInfo().getEmail());
                }

                payment = paymentRepository.save(payment);

                // Update order status to PAID
                order.setStatus(OrderStatus.PAID);
                order.setNotes("Payment completed successfully. Stock decreased. Order ready for fulfillment.");
                orderRepository.save(order);

                log.info("Payment executed successfully for order: {} with transaction ID: {}",
                        order.getOrderNumber(), saleId);

                // ✅ CLEAR USER'S CART AFTER SUCCESSFUL PAYMENT
                try {
                    Long userId = order.getUser().getId();
                    cartRepository.findByUserId(userId).ifPresent(cart -> {
                        cartItemRepository.deleteByCartId(cart.getId());
                        log.info("✅ Cart cleared for user {} after successful payment", userId);
                    });

                    // ✅ CLEAR REDIS CART CACHE
                    redisCartCacheService.invalidateCart(userId);
                    log.info("✅ Redis cart cache cleared for user {}", userId);
                } catch (Exception cartError) {
                    // Don't fail the payment if cart clearing fails
                    log.error("❌ Failed to clear cart after payment, but payment was successful", cartError);
                }

                // ✅ SEND EMAIL TO CUSTOMER
                try {
                    log.info("📧 Starting email notification process (EXECUTE endpoint)...");

                    // Get customer email - prioritize shipping email, fallback to user email
                    String customerEmail = order.getShippingEmail();
                    if (customerEmail == null || customerEmail.isEmpty()) {
                        customerEmail = order.getUser().getEmail();
                    }

                    log.info("Customer email: {}", customerEmail);
                    log.info("Order number: {}", order.getOrderNumber());
                    log.info("Building order details HTML...");

                    // Build email content
                    String orderDetails = buildOrderDetailsForEmail(order, payment);

                    log.info("Order details HTML length: {} characters", orderDetails.length());
                    log.info("Sending email via EmailService...");

                    // Send email
                    emailService.sendOrderConfirmationEmail(
                            customerEmail,
                            order.getOrderNumber(),
                            orderDetails
                    );

                    log.info("✅ Payment confirmation email SENT to {} via EXECUTE endpoint", customerEmail);

                } catch (Exception emailError) {
                    // Don't fail the payment if email fails - webhook will retry
                    log.error("❌ Failed to send payment confirmation email via EXECUTE endpoint", emailError);
                    log.warn("⚠️ Email failed but payment succeeded. Webhook will retry sending email.");
                    emailError.printStackTrace();
                }

                return ResponseEntity.ok(Map.of(
                        "message", "Payment completed successfully",
                        "paymentId", payment.getId(),
                        "transactionId", saleId,
                        "status", payment.getStatus(),
                        "orderNumber", order.getOrderNumber(),
                        "orderStatus", order.getStatus()
                ));

            } else {
                // Payment failed
                payment.markAsFailed("Payment not approved. State: " + executedPayment.getState());
                payment.setPaymentDetails(objectMapper.writeValueAsString(executedPayment));
                paymentRepository.save(payment);

                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Payment was not approved"));
            }

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (PayPalRESTException e) {
            log.error("PayPal API error while executing payment", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "PayPal service error: " + e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to execute payment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to execute payment: " + e.getMessage()));
        }
    }

    /**
     * Get payment details
     * GET /api/payments/{paymentId}
     */
    @GetMapping("/{paymentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getPayment(@PathVariable Long paymentId) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();

            com.ut.edu.backend.payment.Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

            // IDOR Protection
            if (!payment.getOrder().getUser().getId().equals(currentUserId) &&
                !authorizationService.isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
            }

            Map<String, Object> response = buildPaymentResponse(payment);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get payment: {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve payment"));
        }
    }

    /**
     * Get payment by order ID
     * GET /api/payments/order/{orderId}
     */
    @GetMapping("/order/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getPaymentByOrderId(@PathVariable Long orderId) {
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

            com.ut.edu.backend.payment.Payment payment = paymentRepository.findByOrderId(orderId)
                    .orElse(null);

            if (payment == null) {
                return ResponseEntity.ok(Map.of(
                        "message", "No payment found for this order",
                        "hasPay payment", false
                ));
            }

            Map<String, Object> response = buildPaymentResponse(payment);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get payment for order: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve payment"));
        }
    }

    /**
     * Refund a payment (Admin only)
     * POST /api/payments/{paymentId}/refund
     *
     * Request body:
     * {
     *   "amount": 100.00,
     *   "reason": "Customer requested refund"
     * }
     */
    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<?> refundPayment(
            @PathVariable Long paymentId,
            @RequestBody RefundRequest request) {
        try {
            // Only payments of the current store can be refunded (anti-IDOR)
            com.ut.edu.backend.payment.Payment payment = paymentRepository.findById(paymentId)
                    .filter(p -> tenantGuard.isCurrentStore(p.getStore()))
                    .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

            // Validate payment can be refunded
            if (!payment.canBeRefunded()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Payment cannot be refunded. Status: " + payment.getStatus()));
            }

            // Validate refund amount
            BigDecimal refundAmount = request.getAmount();
            if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid refund amount"));
            }

            BigDecimal maxRefundAmount = payment.getAmount().subtract(payment.getRefundAmount());
            if (refundAmount.compareTo(maxRefundAmount) > 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Refund amount exceeds available amount: " + maxRefundAmount));
            }

            // Process refund via PayPal
            String saleId = payment.getTransactionId();
            if (saleId == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No transaction ID found for refund"));
            }

            com.paypal.api.payments.DetailedRefund refund = payPalService.refundPayment(saleId, refundAmount);

            // Update payment record
            payment.markAsRefunded(payment.getRefundAmount().add(refundAmount));
            payment.setNotes(request.getReason());
            payment = paymentRepository.save(payment);

            // Update order status if fully refunded
            Order order = payment.getOrder();
            if (payment.getStatus() == PaymentStatus.REFUNDED) {
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
            }

            log.info("Payment refunded successfully. Payment ID: {}, Amount: {}", paymentId, refundAmount);

            return ResponseEntity.ok(Map.of(
                    "message", "Refund processed successfully",
                    "paymentId", payment.getId(),
                    "refundAmount", refundAmount,
                    "totalRefunded", payment.getRefundAmount(),
                    "refundId", refund.getId(),
                    "status", payment.getStatus()
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (PayPalRESTException e) {
            log.error("PayPal API error while processing refund", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "PayPal refund error: " + e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to process refund for payment: {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process refund"));
        }
    }

    /**
     * PayPal webhook endpoint
     * POST /api/payments/webhook/paypal
     *
     * Handles PayPal IPN/Webhook notifications
     */
    @PostMapping("/webhook/paypal")
    public ResponseEntity<?> handlePayPalWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-ID", required = false) String transmissionId,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-TIME", required = false) String transmissionTime,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-SIG", required = false) String transmissionSig,
            @RequestHeader(value = "PAYPAL-CERT-URL", required = false) String certUrl,
            @RequestHeader(value = "PAYPAL-AUTH-ALGO", required = false) String authAlgo,
            HttpServletRequest httpRequest) {
        try {
            log.info("Received PayPal webhook notification");
            log.info("Headers - ID: {}, Time: {}, Sig: {}, Cert: {}, Algo: {}",
                    transmissionId, transmissionTime, transmissionSig, certUrl, authAlgo);


            // Verify webhook signature (important for production)
            if (!payPalService.verifyWebhookSignature(
                    payload, transmissionId, transmissionTime,
                    transmissionSig, certUrl, authAlgo)) {
                log.warn("Invalid PayPal webhook signature");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid signature"));
            }


            // Parse webhook payload
            Map<String, Object> webhookData = objectMapper.readValue(payload, Map.class);
            String eventType = (String) webhookData.get("event_type");

            log.info("PayPal webhook event type: {}", eventType);

            // Handle different event types
            switch (eventType) {
                case "PAYMENT.SALE.COMPLETED":
                    handlePaymentCompleted(webhookData);
                    break;

                case "PAYMENT.SALE.REFUNDED":
                    handlePaymentRefunded(webhookData);
                    break;

                case "PAYMENT.SALE.REVERSED":
                    handlePaymentReversed(webhookData);
                    break;

                default:
                    log.info("Unhandled webhook event type: {}", eventType);
            }

            return ResponseEntity.ok(Map.of("message", "Webhook processed successfully"));

        } catch (Exception e) {
            log.error("Failed to process PayPal webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process webhook"));
        }
    }

    

    // ==================== HELPER METHODS ====================

    /**
     * Build payment response
     */
    private Map<String, Object> buildPaymentResponse(com.ut.edu.backend.payment.Payment payment) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", payment.getId());
        response.put("orderId", payment.getOrder().getId());
        response.put("orderNumber", payment.getOrder().getOrderNumber());
        response.put("paymentMethod", payment.getPaymentMethod());
        response.put("status", payment.getStatus());
        response.put("amount", payment.getAmount());
        response.put("currency", payment.getCurrency());
        response.put("transactionId", payment.getTransactionId());
        response.put("paypalOrderId", payment.getPaypalOrderId());
        response.put("paypalPayerEmail", payment.getPaypalPayerEmail());
        response.put("paymentDate", payment.getPaymentDate());
        response.put("refundAmount", payment.getRefundAmount());
        response.put("refundDate", payment.getRefundDate());
        response.put("failureReason", payment.getFailureReason());
        response.put("createdAt", payment.getCreatedAt());
        response.put("updatedAt", payment.getUpdatedAt());

        return response;
    }

    /**
     * Handle payment completed webhook
     * JSON structure:
     * {
     *   "resource": {
     *     "id": "saleId",
     *     "parent_payment": "paymentId",
     *     "amount": { "total": "13.37", "currency": "USD" }
     *   }
     * }
     */
    @SuppressWarnings("unchecked")
    private void handlePaymentCompleted(Map<String, Object> webhookData) {
        try {
            log.info("=== START handlePaymentCompleted ===");
            log.info("Webhook data: {}", webhookData);

            Map<String, Object> resource = (Map<String, Object>) webhookData.get("resource");
            String parentPaymentId = (String) resource.get("parent_payment");

            log.info("Payment completed for PayPal payment ID: {}", parentPaymentId);

            // Find payment by PayPal payment ID
            com.ut.edu.backend.payment.Payment payment = paymentRepository
                    .findByPaypalOrderId(parentPaymentId)
                    .orElse(null);

            if (payment == null) {
                log.error("Payment NOT FOUND for PayPal Order ID: {}", parentPaymentId);
                return;
            }

            log.info("Found payment ID: {}, current status: {}", payment.getId(), payment.getStatus());

            if (payment.getStatus() != PaymentStatus.COMPLETED) {
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setPaymentDate(LocalDateTime.now());
                paymentRepository.save(payment);

                // Update order status
                Order order = payment.getOrder();
                order.setStatus(OrderStatus.PAID);
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);

                log.info("✅ Payment {} marked as COMPLETED via webhook", payment.getId());

                // ✅ CLEAR USER'S CART AFTER SUCCESSFUL PAYMENT (webhook)
                try {
                    Long userId = order.getUser().getId();
                    cartRepository.findByUserId(userId).ifPresent(cart -> {
                        cartItemRepository.deleteByCartId(cart.getId());
                        log.info("✅ Cart cleared for user {} after successful payment (webhook)", userId);
                    });

                    // ✅ CLEAR REDIS CART CACHE
                    redisCartCacheService.invalidateCart(userId);
                    log.info("✅ Redis cart cache cleared for user {} (webhook)", userId);
                } catch (Exception cartError) {
                    // Don't fail the webhook if cart clearing fails
                    log.error("❌ Failed to clear cart after payment (webhook), but payment was successful", cartError);
                }

                // Send payment confirmation email to customer
                try {
                    log.info("📧 Starting email notification process...");

                    String customerEmail = order.getShippingEmail();
                    if (customerEmail == null || customerEmail.isEmpty()) {
                        customerEmail = order.getUser().getEmail();
                    }

                    log.info("Customer email: {}", customerEmail);
                    log.info("Order number: {}", order.getOrderNumber());
                    log.info("Building order details HTML...");

                    String orderDetails = buildOrderDetailsForEmail(order, payment);

                    log.info("Order details HTML length: {} characters", orderDetails.length());
                    log.info("Sending email via EmailService...");

                    emailService.sendOrderConfirmationEmail(
                            customerEmail,
                            order.getOrderNumber(),
                            orderDetails
                    );

                    log.info("✅ Payment confirmation email SENT to {}", customerEmail);
                } catch (Exception emailError) {
                    // Don't fail the webhook if email fails
                    log.error("❌ Failed to send payment confirmation email", emailError);
                    emailError.printStackTrace();
                }
            } else {
                log.warn("⚠️ Payment {} already COMPLETED, skipping", payment.getId());
            }

            log.info("=== END handlePaymentCompleted ===");
        } catch (Exception e) {
            log.error("❌ Error handling payment completed webhook", e);
            e.printStackTrace();
        }
    }

    /**
     * Handle payment refunded webhook
     * JSON structure:
     * {
     *   "resource": {
     *     "id": "refundId",
     *     "sale_id": "saleId",
     *     "parent_payment": "paymentId",
     *     "amount": { "total": "13.37", "currency": "USD" },
     *     "state": "completed"
     *   }
     * }
     */
    @SuppressWarnings("unchecked")
    private void handlePaymentRefunded(Map<String, Object> webhookData) {
        try {
            Map<String, Object> resource = (Map<String, Object>) webhookData.get("resource");
            String parentPaymentId = (String) resource.get("parent_payment");
            Map<String, Object> amountData = (Map<String, Object>) resource.get("amount");
            String refundAmountStr = (String) amountData.get("total");
            BigDecimal refundAmount = new BigDecimal(refundAmountStr);

            log.info("Refund webhook received for PayPal payment ID: {}, amount: {}",
                    parentPaymentId, refundAmount);

            // Find payment by PayPal payment ID
            com.ut.edu.backend.payment.Payment payment = paymentRepository
                    .findByPaypalOrderId(parentPaymentId)
                    .orElse(null);

            if (payment != null) {
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.setRefundAmount(refundAmount);
                payment.setRefundDate(LocalDateTime.now());
                payment.setUpdatedAt(LocalDateTime.now());
                paymentRepository.save(payment);

                // Update order status
                Order order = payment.getOrder();
                order.setStatus(OrderStatus.REFUNDED);
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);

                log.info("Payment {} marked as REFUNDED via webhook, amount: {}",
                        payment.getId(), refundAmount);

                // Send refund confirmation email to customer
                try {
                    String customerEmail = order.getShippingEmail();
                    if (customerEmail == null || customerEmail.isEmpty()) {
                        customerEmail = order.getUser().getEmail();
                    }

                    String refundDetails = buildRefundDetailsForEmail(order, payment, refundAmount);
                    emailService.sendOrderConfirmationEmail(
                            customerEmail,
                            "REFUND - " + order.getOrderNumber(),
                            refundDetails
                    );

                    log.info("Refund confirmation email sent to {}", customerEmail);
                } catch (Exception emailError) {
                    log.error("Failed to send refund confirmation email", emailError);
                }
            }
        } catch (Exception e) {
            log.error("Error handling payment refunded webhook", e);
        }
    }

    /**
     * Handle payment reversed webhook (chargeback/dispute)
     * JSON structure:
     * {
     *   "resource": {
     *     "id": "saleId",
     *     "parent_payment": "paymentId",
     *     "state": "reversed",
     *     "reason_code": "CHARGEBACK"
     *   }
     * }
     */
    @SuppressWarnings("unchecked")
    private void handlePaymentReversed(Map<String, Object> webhookData) {
        try {
            Map<String, Object> resource = (Map<String, Object>) webhookData.get("resource");
            String parentPaymentId = (String) resource.get("parent_payment");
            String reasonCode = (String) resource.get("reason_code");

            log.warn("Payment reversed webhook received for PayPal payment ID: {}, reason: {}",
                    parentPaymentId, reasonCode);

            // Find payment by PayPal payment ID
            com.ut.edu.backend.payment.Payment payment = paymentRepository
                    .findByPaypalOrderId(parentPaymentId)
                    .orElse(null);

            if (payment != null) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Payment reversed: " + reasonCode);
                payment.setUpdatedAt(LocalDateTime.now());
                paymentRepository.save(payment);

                // Update order status to cancelled
                Order order = payment.getOrder();
                order.setStatus(OrderStatus.CANCELLED);
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);

                log.warn("Payment {} marked as FAILED due to reversal, reason: {}",
                        payment.getId(), reasonCode);
            }
        } catch (Exception e) {
            log.error("Error handling payment reversed webhook", e);
        }
    }

    /**
     * Format amount to Vietnamese currency
     */
    private String formatVND(BigDecimal amount) {
        if (amount == null) {
            return "0 ₫";
        }
        // Format: 1.234.567 ₫
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,###");
        java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols(java.util.Locale.forLanguageTag("vi-VN"));
        symbols.setGroupingSeparator('.');
        df.setDecimalFormatSymbols(symbols);
        return df.format(amount) + " ₫";
    }

    /**
     * Build order details HTML/text for email notification
     */
    private String buildOrderDetailsForEmail(Order order, com.ut.edu.backend.payment.Payment payment) {
        StringBuilder sb = new StringBuilder();

        sb.append("<html><body style='font-family: Arial, sans-serif;'>");
        sb.append("<h2 style='color: #4CAF50;'>Thanh Toán Thành Công!</h2>");
        sb.append("<p>Cảm ơn bạn đã thanh toán. Đơn hàng của bạn đã được xác nhận.</p>");

        sb.append("<h3>Chi Tiết Đơn Hàng</h3>");
        sb.append("<table style='border-collapse: collapse; width: 100%;'>");
        sb.append("<tr><td style='padding: 8px; border: 1px solid #ddd;'><strong>Mã Đơn Hàng:</strong></td>");
        sb.append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(order.getOrderNumber()).append("</td></tr>");
        sb.append("<tr><td style='padding: 8px; border: 1px solid #ddd;'><strong>Ngày Thanh Toán:</strong></td>");
        sb.append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(payment.getPaymentDate()).append("</td></tr>");
        sb.append("<tr><td style='padding: 8px; border: 1px solid #ddd;'><strong>Số Tiền Đã Thanh Toán:</strong></td>");
        sb.append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(formatVND(order.getTotal())).append("</td></tr>");
        sb.append("<tr><td style='padding: 8px; border: 1px solid #ddd;'><strong>Phương Thức Thanh Toán:</strong></td>");
        sb.append("<td style='padding: 8px; border: 1px solid #ddd;'>PayPal</td></tr>");
        sb.append("<tr><td style='padding: 8px; border: 1px solid #ddd;'><strong>Mã Giao Dịch:</strong></td>");
        sb.append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(payment.getTransactionId()).append("</td></tr>");
        sb.append("</table>");

        sb.append("<h3>Sản Phẩm Đã Đặt</h3>");
        sb.append("<table style='border-collapse: collapse; width: 100%;'>");
        sb.append("<tr style='background-color: #f2f2f2;'>");
        sb.append("<th style='padding: 8px; border: 1px solid #ddd; text-align: left;'>Sản Phẩm</th>");
        sb.append("<th style='padding: 8px; border: 1px solid #ddd; text-align: center;'>Số Lượng</th>");
        sb.append("<th style='padding: 8px; border: 1px solid #ddd; text-align: right;'>Đơn Giá</th>");
        sb.append("<th style='padding: 8px; border: 1px solid #ddd; text-align: right;'>Tổng Phụ</th>");
        sb.append("</tr>");

        for (OrderItem item : order.getItems()) {
            sb.append("<tr>");
            sb.append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(item.getProductName()).append("</td>");
            sb.append("<td style='padding: 8px; border: 1px solid #ddd; text-align: center;'>").append(item.getQuantity()).append("</td>");
            sb.append("<td style='padding: 8px; border: 1px solid #ddd; text-align: right;'>").append(formatVND(item.getUnitPrice())).append("</td>");
            sb.append("<td style='padding: 8px; border: 1px solid #ddd; text-align: right;'>").append(formatVND(item.getSubtotal())).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");

        sb.append("<h3>Địa Chỉ Giao Hàng</h3>");
        sb.append("<p>");
        sb.append(order.getShippingAddressLine1()).append("<br>");
        if (order.getShippingAddressLine2() != null && !order.getShippingAddressLine2().isEmpty()) {
            sb.append(order.getShippingAddressLine2()).append("<br>");
        }
        sb.append(order.getShippingCity()).append(", ").append(order.getShippingStateProvince()).append(" ")
          .append(order.getShippingPostalCode()).append("<br>");
        sb.append(order.getShippingCountry());
        sb.append("</p>");

        sb.append("<h3>Tóm Tắt Đơn Hàng</h3>");
        sb.append("<table style='border-collapse: collapse; width: 100%;'>");
        sb.append("<tr><td style='padding: 8px;'><strong>Tổng Phụ:</strong></td>");
        sb.append("<td style='padding: 8px; text-align: right;'>").append(formatVND(order.getSubtotal())).append("</td></tr>");
        sb.append("<tr><td style='padding: 8px;'><strong>Vận Chuyển:</strong></td>");
        sb.append("<td style='padding: 8px; text-align: right;'>").append(formatVND(order.getShippingCost())).append("</td></tr>");
        sb.append("<tr><td style='padding: 8px;'><strong>Thuế:</strong></td>");
        sb.append("<td style='padding: 8px; text-align: right;'>").append(formatVND(order.getTaxAmount())).append("</td></tr>");
        if (order.getDiscountAmount() != null && order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("<tr><td style='padding: 8px;'><strong>Giảm Giá:</strong></td>");
            sb.append("<td style='padding: 8px; text-align: right; color: green;'>-").append(formatVND(order.getDiscountAmount())).append("</td></tr>");
        }
        sb.append("<tr style='border-top: 2px solid #333;'><td style='padding: 8px;'><strong>Tổng Cộng:</strong></td>");
        sb.append("<td style='padding: 8px; text-align: right; font-size: 18px; color: #4CAF50;'><strong>")
          .append(formatVND(order.getTotal())).append("</strong></td></tr>");
        sb.append("</table>");

        sb.append("<p style='margin-top: 30px; color: #666;'>Nếu bạn có bất kỳ câu hỏi nào, vui lòng liên hệ đội ngũ hỗ trợ của chúng tôi.</p>");
        sb.append("</body></html>");

        return sb.toString();
    }

    /**
     * Build refund details HTML/text for email notification
     */
    private String buildRefundDetailsForEmail(Order order, com.ut.edu.backend.payment.Payment payment, BigDecimal refundAmount) {
        StringBuilder sb = new StringBuilder();

        sb.append("<html><body style='font-family: Arial, sans-serif;'>");
        sb.append("<h2 style='color: #FF9800;'>Refund Processed</h2>");
        sb.append("<p>Your refund has been processed successfully.</p>");

        sb.append("<h3>Refund Details</h3>");
        sb.append("<table style='border-collapse: collapse; width: 100%;'>");
        sb.append("<tr><td style='padding: 8px; border: 1px solid #ddd;'><strong>Order Number:</strong></td>");
        sb.append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(order.getOrderNumber()).append("</td></tr>");
        sb.append("<tr><td style='padding: 8px; border: 1px solid #ddd;'><strong>Refund Date:</strong></td>");
        sb.append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(payment.getRefundDate()).append("</td></tr>");
        sb.append("<tr><td style='padding: 8px; border: 1px solid #ddd;'><strong>Refund Amount:</strong></td>");
        sb.append("<td style='padding: 8px; border: 1px solid #ddd; color: green; font-size: 18px;'><strong>")
          .append(formatVND(refundAmount)).append("</strong></td></tr>");
        sb.append("<tr><td style='padding: 8px; border: 1px solid #ddd;'><strong>Transaction ID:</strong></td>");
        sb.append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(payment.getTransactionId()).append("</td></tr>");
        sb.append("</table>");

        sb.append("<p style='margin-top: 20px;'>The refund will appear in your PayPal account or original payment method within 5-10 business days.</p>");
        sb.append("<p style='color: #666;'>If you have any questions about this refund, please contact our support team.</p>");
        sb.append("</body></html>");

        return sb.toString();
    }

    // ==================== REQUEST DTOs ====================

    /**
     * Refund request DTO
     */
    @lombok.Data
    public static class RefundRequest {
        @jakarta.validation.constraints.NotNull(message = "Refund amount is required")
        @jakarta.validation.constraints.DecimalMin(value = "0.01", message = "Refund amount must be greater than 0")
        private BigDecimal amount;

        private String reason;
    }
}

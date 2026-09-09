package com.ut.edu.backend.order;

import com.ut.edu.backend.validation.OrderStatusValidator;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.payment.PaymentMethod;
import com.ut.edu.backend.payment.PaymentRepository;
import com.ut.edu.backend.payment.PaymentStatus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Store Order Controller (owner dashboard)
 * Order management for store owners/managers, scoped to their store.
 * List/stats queries are tenant-scoped by the Hibernate filter; findById
 * bypasses it, so every by-id access goes through findStoreOrder().
 */
@RestController
@RequestMapping("/store/orders")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@Slf4j
@Transactional
public class AdminOrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStatusValidator statusValidator;

    @Autowired
    private TenantGuard tenantGuard;

    @Autowired
    private PaymentRepository paymentRepository;

    /**
     * Load an order only if it belongs to the current store; cross-tenant
     * ids look like "not found" (anti-IDOR).
     */
    private Order findStoreOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .filter(o -> tenantGuard.isCurrentStore(o.getStore()))
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    /**
     * The statuses "Đặt hàng" shows when the shop has not touched the
     * checkboxes: everything still in play. A cancelled, failed or refunded
     * order is history the shop opts into seeing, the same way KiotViet leaves
     * "Đã hủy" unticked on its own order list.
     */
    private static final List<OrderStatus> DEFAULT_STATUSES = List.of(
            OrderStatus.PENDING,
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.PENDING_COD,
            OrderStatus.PAID,
            OrderStatus.PROCESSING,
            OrderStatus.SHIPPED,
            OrderStatus.DELIVERED);

    /**
     * GET /api/store/orders?statuses=PENDING&statuses=PAID&from=2026-09-01&to=2026-09-30&query=ORD123&page=0&size=15
     *
     * Backs the dashboard's "Đặt hàng" list: Trạng thái checkboxes, a Thời
     * gian range over the order date and a search on the order code. Shaped
     * like PurchaseOrderController.list - including loading the full match set
     * so the totals row sums every matching order rather than the page in
     * front of the user, which is fine at this app's scale.
     */
    @GetMapping
    public ResponseEntity<?> getAllOrders(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<String> paymentMethods,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        try {
            List<OrderStatus> statusList = (statuses == null || statuses.isEmpty())
                    ? DEFAULT_STATUSES
                    : statuses.stream().map(OrderStatus::valueOf).collect(Collectors.toList());

            Specification<Order> spec = Specification.where(
                    (root, q, cb) -> root.get("status").in(statusList));
            if (from != null && !from.isBlank()) {
                LocalDateTime fromDt = LocalDate.parse(from).atStartOfDay();
                spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromDt));
            }
            if (to != null && !to.isBlank()) {
                LocalDateTime toDt = LocalDate.parse(to).atTime(LocalTime.MAX);
                spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), toDt));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("orderNumber")), like));
            }
            if (paymentMethods != null && !paymentMethods.isEmpty()) {
                List<PaymentMethod> methods = paymentMethods.stream()
                        .map(PaymentMethod::valueOf)
                        .collect(Collectors.toList());
                // An inner join here is deliberate: an order with no payment
                // record yet has no method to match, so it drops out.
                spec = spec.and((root, q, cb) -> root.join("payment").get("paymentMethod").in(methods));
            }

            List<Order> all = orderRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
            List<StoreOrderResponse> summaries = all.stream()
                    .map(StoreOrderResponse::summary)
                    .collect(Collectors.toList());

            BigDecimal totalAmount = summaries.stream()
                    .map(StoreOrderResponse::total)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalPaid = summaries.stream()
                    .map(StoreOrderResponse::amountPaid)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int totalItems = summaries.size();
            int fromIndex = Math.min(page * size, totalItems);
            int toIndex = Math.min(fromIndex + size, totalItems);

            Map<String, Object> response = new HashMap<>();
            response.put("orders", summaries.subList(fromIndex, toIndex));
            response.put("currentPage", page);
            response.put("totalItems", totalItems);
            response.put("totalPages", size > 0 ? (int) Math.ceil((double) totalItems / size) : 0);
            response.put("totalAmount", totalAmount);
            response.put("totalPaid", totalPaid);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid filter: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve orders"));
        }
    }

    /**
     * Get order by ID
     * GET /api/store/orders/{orderId}
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrderById(@PathVariable Long orderId) {
        try {
            Order order = findStoreOrder(orderId);

            return ResponseEntity.ok(StoreOrderResponse.detail(order));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get order: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve order"));
        }
    }

    /**
     * Update order status
     * PATCH /api/admin/orders/{orderId}/status
     */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> request) {
        try {
            Order order = findStoreOrder(orderId);

            String statusStr = request.get("status");
            if (statusStr == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Status is required"));
            }

            // Parse and validate status with validator (admin version with detailed errors)
            OrderStatus newStatus;
            try {
                newStatus = statusValidator.parseAndValidateStatusForAdmin(statusStr);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", e.getMessage()));
            }

            // Check if admin can manually set this status
            if (!statusValidator.canAdminSetStatus(newStatus)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Status '" + newStatus + "' cannot be set manually by admin"));
            }

            OrderStatus oldStatus = order.getStatus();

            // Validate status transition
            String validationMessage = statusValidator.getTransitionValidationMessage(oldStatus, newStatus);
            if (validationMessage != null) {
                log.warn("Invalid status transition attempt for order {}: {}", orderId, validationMessage);
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "error", validationMessage,
                                "currentStatus", oldStatus,
                                "requestedStatus", newStatus,
                                "allowedTransitions", statusValidator.getAllowedTransitions(oldStatus)
                        ));
            }

            // Apply status change
            order.setStatus(newStatus);
            orderRepository.save(order);

            // COD: shop staff confirming delivery IS collecting the cash -
            // there's no gateway callback ever coming for COD (see
            // CodPaymentProvider). Scoped tightly: only on the DELIVERED
            // transition, only for a CASH_ON_DELIVERY order whose payment is
            // still PENDING (idempotent against retries/double-PATCHes, and
            // inert for PayPal/MoMo since their Payment is already COMPLETED
            // long before DELIVERED).
            if (newStatus == OrderStatus.DELIVERED) {
                paymentRepository.findByOrderId(order.getId())
                        .filter(p -> p.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY)
                        .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                        .ifPresent(p -> {
                            p.markAsPaid();
                            paymentRepository.save(p);
                            log.info("COD payment {} marked COMPLETED on delivery confirmation for order {}", p.getId(), orderId);
                        });
            }

            log.info("Order {} status updated from {} to {} by admin", orderId, oldStatus, newStatus);

            return ResponseEntity.ok(Map.of(
                    "message", "Order status updated successfully",
                    "orderId", orderId,
                    "oldStatus", oldStatus,
                    "newStatus", newStatus
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update order status: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update order status"));
        }
    }

    /**
     * Add tracking information
     * PATCH /api/admin/orders/{orderId}/tracking
     */
    @PatchMapping("/{orderId}/tracking")
    public ResponseEntity<?> updateTrackingInfo(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> request) {
        try {
            Order order = findStoreOrder(orderId);

            String trackingNumber = request.get("trackingNumber");
            String shippingCarrier = request.get("shippingCarrier");

            if (trackingNumber != null) {
                order.setTrackingNumber(trackingNumber);
            }
            if (shippingCarrier != null) {
                order.setShippingCarrier(shippingCarrier);
            }

            // Update status to SHIPPED if not already (only if status allows it)
            OrderStatus currentStatus = order.getStatus();
            if (currentStatus != null &&
                currentStatus != OrderStatus.SHIPPED &&
                currentStatus != OrderStatus.DELIVERED &&
                currentStatus != OrderStatus.CANCELLED) {
                order.setStatus(OrderStatus.SHIPPED);
            }

            orderRepository.save(order);

            log.info("Tracking info added to order {}: {} - {}", orderId, shippingCarrier, trackingNumber);

            return ResponseEntity.ok(Map.of(
                    "message", "Tracking information updated successfully",
                    "orderId", orderId,
                    "trackingNumber", trackingNumber != null ? trackingNumber : order.getTrackingNumber(),
                    "shippingCarrier", shippingCarrier != null ? shippingCarrier : order.getShippingCarrier()
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update tracking info: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update tracking information"));
        }
    }

    /**
     * Add admin notes to order
     * PATCH /api/admin/orders/{orderId}/notes
     */
    @PatchMapping("/{orderId}/notes")
    public ResponseEntity<?> updateAdminNotes(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> request) {
        try {
            Order order = findStoreOrder(orderId);

            String adminNotes = request.get("adminNotes");
            order.setAdminNotes(adminNotes);
            orderRepository.save(order);

            log.info("Admin notes updated for order {}", orderId);

            return ResponseEntity.ok(Map.of(
                    "message", "Admin notes updated successfully",
                    "orderId", orderId
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update admin notes: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update admin notes"));
        }
    }

    /**
     * Get order statistics
     * GET /api/admin/orders/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getOrderStatistics() {
        try {
            long totalOrders = orderRepository.count();

            // Count by status
            Map<String, Long> ordersByStatus = orderRepository.findAll().stream()
                    .collect(Collectors.groupingBy(
                            order -> order.getStatus().toString(),
                            Collectors.counting()
                    ));

            // Calculate total revenue
            BigDecimal totalRevenue = orderRepository.findAll().stream()
                    .filter(order -> order.getStatus() == OrderStatus.DELIVERED ||
                                   order.getStatus() == OrderStatus.PAID)
                    .map(Order::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalOrders", totalOrders);
            stats.put("ordersByStatus", ordersByStatus);
            stats.put("totalRevenue", totalRevenue);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Failed to get order statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve statistics"));
        }
    }

    /**
     * Search orders by order number or user email
     * GET /api/admin/orders/search?query=ORD-12345
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchOrders(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);

            // Search by order number - filter in memory for now
           
            List<Order> allOrders = orderRepository.findAll();
            List<Order> matchedOrders = allOrders.stream()
                    .filter(order -> order.getOrderNumber().toLowerCase()
                            .contains(query.toLowerCase()))
                    .skip((long) page * size)
                    .limit(size)
                    .collect(Collectors.toList());

            long totalMatched = allOrders.stream()
                    .filter(order -> order.getOrderNumber().toLowerCase()
                            .contains(query.toLowerCase()))
                    .count();

            Map<String, Object> response = new HashMap<>();
            response.put("orders", matchedOrders);
            response.put("currentPage", page);
            response.put("totalItems", totalMatched);
            response.put("totalPages", (totalMatched + size - 1) / size);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to search orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to search orders"));
        }
    }

    /**
     * Cancel order (admin action)
     * POST /api/admin/orders/{orderId}/cancel
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable Long orderId,
            @RequestBody(required = false) Map<String, String> request) {
        try {
            Order order = findStoreOrder(orderId);

            if (!order.canBeCancelled()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Order cannot be cancelled in current status: " + order.getStatus()));
            }

            order.setStatus(OrderStatus.CANCELLED);

            // Add cancellation reason to admin notes
            if (request != null && request.containsKey("reason")) {
                String reason = request.get("reason");
                String notes = order.getAdminNotes() != null ? order.getAdminNotes() : "";
                order.setAdminNotes(notes + "\nCancelled by admin: " + reason);
            }

            orderRepository.save(order);

            log.warn("Order {} cancelled by admin", orderId);

            return ResponseEntity.ok(Map.of(
                    "message", "Order cancelled successfully",
                    "orderId", orderId
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to cancel order: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to cancel order"));
        }
    }

    /**
     * Get recent orders
     * GET /api/admin/orders/recent?limit=10
     */
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentOrders(@RequestParam(defaultValue = "10") int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit, Sort.by("createdAt").descending());
            Page<Order> orders = orderRepository.findAll(pageable);

            return ResponseEntity.ok(Map.of(
                    "orders", orders.getContent(),
                    "count", orders.getContent().size()
            ));

        } catch (Exception e) {
            log.error("Failed to get recent orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve recent orders"));
        }
    }

    /**
     * Get allowed status transitions for an order
     * GET /api/admin/orders/{orderId}/allowed-transitions
     */
    @GetMapping("/{orderId}/allowed-transitions")
    public ResponseEntity<?> getAllowedTransitions(@PathVariable Long orderId) {
        try {
            Order order = findStoreOrder(orderId);

            OrderStatus currentStatus = order.getStatus();
            Set<OrderStatus> allowedTransitions = statusValidator.getAllowedTransitions(currentStatus);
            boolean isTerminal = statusValidator.isTerminalStatus(currentStatus);

            return ResponseEntity.ok(Map.of(
                    "orderId", orderId,
                    "currentStatus", currentStatus,
                    "allowedTransitions", allowedTransitions,
                    "isTerminalStatus", isTerminal
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get allowed transitions: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve allowed transitions"));
        }
    }
}

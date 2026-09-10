package com.ut.edu.backend.order;

import com.ut.edu.backend.validation.OrderStatusValidator;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.payment.PaymentMethod;
import com.ut.edu.backend.payment.PaymentRepository;
import com.ut.edu.backend.payment.PaymentStatus;
import com.ut.edu.backend.sale.SalePaymentMethod;
import com.ut.edu.backend.shipping.goship.Shipment;
import com.ut.edu.backend.user.User;
import com.ut.edu.backend.common.SequentialCodeGenerator;
import com.ut.edu.backend.security.AuthorizationService;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;

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

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private OrderDeliverySync deliverySync;

    @Autowired
    private com.ut.edu.backend.shipping.goship.GoshipShipmentService shipmentService;

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
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String tracking,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) List<String> paymentMethods,
            @RequestParam(required = false) List<String> carriers,
            @RequestParam(required = false) List<String> channels,
            @RequestParam(required = false) List<String> creators,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String deliveryFrom,
            @RequestParam(required = false) String deliveryTo,
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
                // The invoice code counts as the order code for a register
                // order: it is what is printed on the receipt in the
                // customer's hand, so it is what gets typed in here.
                spec = spec.and((root, q, cb) -> {
                    q.distinct(true);
                    return cb.or(cb.like(cb.lower(root.get("orderNumber")), like),
                            cb.like(cb.lower(root.join("sale", JoinType.LEFT).get("code")), like));
                });
            }
            if (product != null && !product.isBlank()) {
                String like = "%" + product.trim().toLowerCase() + "%";
                // The line's snapshot columns, so an order for a since-deleted
                // product is still findable by what it was sold as.
                spec = spec.and((root, q, cb) -> {
                    q.distinct(true);
                    var line = root.join("items");
                    return cb.or(cb.like(cb.lower(line.get("productName")), like),
                            cb.like(cb.lower(line.get("productSku")), like));
                });
            }
            if (customer != null && !customer.isBlank()) {
                String like = "%" + customer.trim().toLowerCase() + "%";
                // Left joins: an order names its buyer on one side or the
                // other - an account for a storefront order, a Customer card
                // for a register one - and the recipient columns catch the
                // case where the parcel goes to somebody else entirely.
                spec = spec.and((root, q, cb) -> {
                    q.distinct(true);
                    var u = root.join("user", JoinType.LEFT);
                    var c = root.join("customer", JoinType.LEFT);
                    return cb.or(cb.like(cb.lower(u.get("username")), like),
                            cb.like(cb.lower(u.get("firstName")), like),
                            cb.like(cb.lower(u.get("lastName")), like),
                            cb.like(cb.lower(u.get("phoneNumber")), like),
                            cb.like(cb.lower(u.get("email")), like),
                            cb.like(cb.lower(c.get("name")), like),
                            cb.like(cb.lower(c.get("code")), like),
                            cb.like(cb.lower(c.get("phone")), like),
                            cb.like(cb.lower(c.get("email")), like),
                            cb.like(cb.lower(root.get("recipientName")), like),
                            cb.like(cb.lower(root.get("shippingPhoneNumber")), like));
                });
            }
            if (tracking != null && !tracking.isBlank()) {
                String like = "%" + tracking.trim().toLowerCase() + "%";
                spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("trackingNumber")), like));
            }
            if (note != null && !note.isBlank()) {
                String like = "%" + note.trim().toLowerCase() + "%";
                spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("notes")), like));
            }
            if (paymentMethods != null && !paymentMethods.isEmpty()) {
                // The two sides of the shop tender differently - a storefront
                // order settles through a gateway PaymentMethod, a register
                // one through SalePaymentMethod lines on its invoice - and
                // BANK_TRANSFER exists in both enums, so each name is looked
                // up in both rather than being assigned to one.
                List<PaymentMethod> gateway = paymentMethods.stream()
                        .map(name -> parseEnum(PaymentMethod.class, name))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                List<SalePaymentMethod> tenders = paymentMethods.stream()
                        .map(name -> parseEnum(SalePaymentMethod.class, name))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (gateway.isEmpty() && tenders.isEmpty()) {
                    throw new IllegalArgumentException("Unknown payment method: " + paymentMethods);
                }
                // Left joins, unlike the inner join this replaced: an order is
                // matched on whichever side it actually settled, and one that
                // has settled on neither still drops out, because neither
                // predicate can be true of it.
                spec = spec.and((root, q, cb) -> {
                    q.distinct(true);
                    List<Predicate> matches = new ArrayList<>();
                    if (!gateway.isEmpty()) {
                        matches.add(root.join("payment", JoinType.LEFT).get("paymentMethod").in(gateway));
                    }
                    if (!tenders.isEmpty()) {
                        matches.add(root.join("sale", JoinType.LEFT)
                                .join("payments", JoinType.LEFT).get("method").in(tenders));
                    }
                    return cb.or(matches.toArray(new Predicate[0]));
                });
            }
            if (carriers != null && !carriers.isEmpty()) {
                spec = spec.and((root, q, cb) -> root.get("shippingCarrier").in(carriers));
            }
            if (channels != null && !channels.isEmpty()) {
                List<SalesChannel> wanted = channels.stream().map(SalesChannel::valueOf).collect(Collectors.toList());
                spec = spec.and((root, q, cb) -> root.get("salesChannel").in(wanted));
            }
            if (creators != null && !creators.isEmpty()) {
                spec = spec.and((root, q, cb) -> root.join("createdBy").get("username").in(creators));
            }
            if (province != null && !province.isBlank()) {
                spec = spec.and((root, q, cb) -> cb.equal(root.get("shippingCity"), province.trim()));
            }
            if (district != null && !district.isBlank()) {
                spec = spec.and((root, q, cb) -> cb.equal(root.get("shippingStateProvince"), district.trim()));
            }
            // "Thoi gian giao hang" - the promised date, a different question
            // from when the order was placed, so it filters on its own column;
            // an order nobody promised a date for has no answer and drops out.
            if (deliveryFrom != null && !deliveryFrom.isBlank()) {
                LocalDateTime fromDt = LocalDate.parse(deliveryFrom).atStartOfDay();
                spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("expectedDeliveryAt"), fromDt));
            }
            if (deliveryTo != null && !deliveryTo.isBlank()) {
                LocalDateTime toDt = LocalDate.parse(deliveryTo).atTime(LocalTime.MAX);
                spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("expectedDeliveryAt"), toDt));
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

            settleCodOnDelivery(order, newStatus);

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

    // ==================== KiotViet's selection toolbar ====================
    //
    // Everything below drives the bar that appears once rows are ticked. They
    // are separate endpoints rather than one "apply this to many" call because
    // they are separate decisions: advancing a status, cancelling, editing
    // metadata and merging fail for different reasons and report differently.
    //
    // All of them are partial by design. A shop ticks fifteen rows and asks to
    // finish them; two are already cancelled. Failing the whole batch for that
    // would make the button useless, so each one reports what it did and names
    // what it skipped, and the screen tells the shop.

    /**
     * Loads every id in a bulk request, dropping any that belong to another
     * store. Cross-tenant ids are not an error here - they are simply not this
     * shop's orders, and saying which ids exist elsewhere would answer a
     * question the caller has no business asking.
     */
    private List<Order> findStoreOrders(List<Long> ids) {
        return orderRepository.findAllById(ids).stream()
                .filter(o -> tenantGuard.isCurrentStore(o.getStore()))
                .collect(Collectors.toList());
    }

    /** Null rather than an exception for a name that is not in this enum - the caller decides whether that is fatal. */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private static Map<String, Object> bulkResult(List<String> updated, Map<String, String> skipped) {
        Map<String, Object> result = new HashMap<>();
        result.put("updated", updated.size());
        result.put("updatedCodes", updated);
        result.put("skipped", skipped);
        return result;
    }

    /**
     * "Xu ly dat hang" / "Ket thuc" - move every ticked order to one status.
     * POST /store/orders/bulk-status
     *
     * The validator owns which moves are legal, so an order that cannot make
     * this particular move is skipped with its reason rather than blocking the
     * rest of the batch.
     */
    @PostMapping("/bulk-status")
    public ResponseEntity<?> bulkUpdateStatus(@Valid @RequestBody BulkOrderRequest request,
                                              @RequestParam String status) {
        try {
            OrderStatus target = statusValidator.parseAndValidateStatusForAdmin(status);
            if (!statusValidator.canAdminSetStatus(target)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Status '" + target + "' cannot be set manually by admin"));
            }

            List<String> updated = new ArrayList<>();
            Map<String, String> skipped = new LinkedHashMap<>();
            for (Order order : findStoreOrders(request.ids())) {
                OrderStatus current = order.getStatus();
                if (current == target) {
                    skipped.put(order.getOrderNumber(), "Đơn đã ở trạng thái này");
                    continue;
                }
                String problem = statusValidator.getTransitionValidationMessage(current, target);
                if (problem != null) {
                    skipped.put(order.getOrderNumber(), problem);
                    continue;
                }
                order.setStatus(target);
                settleCodOnDelivery(order, target);
                orderRepository.save(order);
                updated.add(order.getOrderNumber());
            }
            log.info("Bulk status {} applied to {} order(s), {} skipped", target, updated.size(), skipped.size());
            return ResponseEntity.ok(bulkResult(updated, skipped));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to bulk update order status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update orders"));
        }
    }

    /**
     * "Huy don" - cancel every ticked order.
     * POST /store/orders/bulk-cancel
     */
    @PostMapping("/bulk-cancel")
    public ResponseEntity<?> bulkCancel(@Valid @RequestBody BulkOrderRequest request) {
        try {
            String reason = request.reason() == null ? "" : request.reason().trim();
            List<String> updated = new ArrayList<>();
            Map<String, String> skipped = new LinkedHashMap<>();
            for (Order order : findStoreOrders(request.ids())) {
                if (!order.canBeCancelled()) {
                    skipped.put(order.getOrderNumber(), "Không thể hủy đơn đang ở trạng thái " + order.getStatus());
                    continue;
                }
                order.setStatus(OrderStatus.CANCELLED);
                if (!reason.isEmpty()) {
                    appendAdminNote(order, "Cancelled by admin: " + reason);
                }
                orderRepository.save(order);
                updated.add(order.getOrderNumber());
            }
            log.warn("Bulk cancel: {} order(s) cancelled, {} skipped", updated.size(), skipped.size());
            return ResponseEntity.ok(bulkResult(updated, skipped));

        } catch (Exception e) {
            log.error("Failed to bulk cancel orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to cancel orders"));
        }
    }

    /**
     * "Sua nguoi nhan dat, kenh ban, ghi chu" - the bulk edit behind "...".
     * POST /store/orders/bulk-update
     *
     * A null field means the form left that box alone; a blank one means the
     * shop cleared it. Without that distinction a form that only sets the
     * channel would wipe every note it touched.
     */
    @PostMapping("/bulk-update")
    public ResponseEntity<?> bulkUpdate(@Valid @RequestBody BulkOrderRequest request) {
        try {
            SalesChannel channel = null;
            if (request.salesChannel() != null && !request.salesChannel().isBlank()) {
                channel = parseEnum(SalesChannel.class, request.salesChannel().trim());
                if (channel == null) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Kênh bán không hợp lệ: " + request.salesChannel()));
                }
            }
            List<String> updated = new ArrayList<>();
            for (Order order : findStoreOrders(request.ids())) {
                if (request.recipientName() != null) {
                    String name = request.recipientName().trim();
                    order.setRecipientName(name.isEmpty() ? null : name);
                }
                if (channel != null) {
                    order.setSalesChannel(channel);
                }
                if (request.notes() != null) {
                    String note = request.notes().trim();
                    order.setNotes(note.isEmpty() ? null : note);
                }
                orderRepository.save(order);
                updated.add(order.getOrderNumber());
            }
            return ResponseEntity.ok(bulkResult(updated, Map.of()));

        } catch (Exception e) {
            log.error("Failed to bulk update orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update orders"));
        }
    }

    /**
     * "Gop don" - fold several of one customer's orders into a single one.
     * POST /store/orders/merge
     *
     * Deliberately narrow, because a merge that gets this wrong duplicates
     * money or loses it:
     *   - one buyer only. Two customers' goods in one parcel is not a merge.
     *   - nothing settled yet (PENDING / PAYMENT_PENDING / PENDING_COD). An
     *     order that has already taken the customer's money cannot have that
     *     money moved onto a different order by a list-screen button.
     *   - the sources are cancelled, never deleted, and each keeps a pointer
     *     to what it became, so the old codes still resolve for the customer
     *     who is holding them.
     */
    @PostMapping("/merge")
    public ResponseEntity<?> mergeOrders(@Valid @RequestBody BulkOrderRequest request) {
        try {
            List<Order> sources = findStoreOrders(request.ids());
            if (sources.size() < 2) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Cần chọn ít nhất 2 đơn đặt hàng để gộp"));
            }
            List<Order> ordered = sources.stream()
                    .sorted(Comparator.comparing(Order::getCreatedAt))
                    .collect(Collectors.toList());

            Order first = ordered.get(0);
            Long userId = first.getUser() != null ? first.getUser().getId() : null;
            Long customerId = first.getCustomer() != null ? first.getCustomer().getId() : null;
            if (userId == null && customerId == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Không gộp được đơn không có khách hàng"));
            }
            for (Order order : ordered) {
                if (!MERGEABLE_STATUSES.contains(order.getStatus())) {
                    return ResponseEntity.badRequest().body(Map.of("error",
                            "Chỉ gộp được đơn chưa thanh toán - " + order.getOrderNumber()
                                    + " đang ở trạng thái " + order.getStatus()));
                }
                Long orderUserId = order.getUser() != null ? order.getUser().getId() : null;
                Long orderCustomerId = order.getCustomer() != null ? order.getCustomer().getId() : null;
                if (!Objects.equals(userId, orderUserId) || !Objects.equals(customerId, orderCustomerId)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Chỉ gộp được các đơn của cùng một khách hàng"));
                }
            }

            Order merged = Order.builder()
                    .store(first.getStore())
                    .user(first.getUser())
                    .customer(first.getCustomer())
                    .createdBy(authorizationService.getCurrentUser())
                    .salesChannel(first.getSalesChannel())
                    .status(first.getStatus())
                    .subtotal(BigDecimal.ZERO)
                    .discountAmount(BigDecimal.ZERO)
                    .otherCollectionAmount(BigDecimal.ZERO)
                    .shippingCost(BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO)
                    .total(BigDecimal.ZERO)
                    // The parcel goes to one address: the earliest order's, which
                    // is the one the customer gave first.
                    .recipientName(first.getRecipientName())
                    .shippingAddressLine1(first.getShippingAddressLine1())
                    .shippingAddressLine2(first.getShippingAddressLine2())
                    .shippingCity(first.getShippingCity())
                    .shippingStateProvince(first.getShippingStateProvince())
                    .shippingWard(first.getShippingWard())
                    .shippingPostalCode(first.getShippingPostalCode())
                    .shippingCountry(first.getShippingCountry())
                    .shippingPhoneNumber(first.getShippingPhoneNumber())
                    .shippingEmail(first.getShippingEmail())
                    .shippingCarrier(first.getShippingCarrier())
                    .expectedDeliveryAt(first.getExpectedDeliveryAt())
                    .build();

            BigDecimal subtotal = BigDecimal.ZERO;
            BigDecimal discount = BigDecimal.ZERO;
            BigDecimal otherCollection = BigDecimal.ZERO;
            BigDecimal shipping = BigDecimal.ZERO;
            BigDecimal tax = BigDecimal.ZERO;
            List<String> sourceCodes = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            for (Order order : ordered) {
                sourceCodes.add(order.getOrderNumber());
                if (order.getNotes() != null && !order.getNotes().isBlank()) {
                    notes.add(order.getOrderNumber() + ": " + order.getNotes().trim());
                }
                subtotal = subtotal.add(nz(order.getSubtotal()));
                discount = discount.add(nz(order.getDiscountAmount()));
                otherCollection = otherCollection.add(nz(order.getOtherCollectionAmount()));
                // Shipping is charged once for one parcel, so the merged order
                // keeps the highest of the source fees rather than their sum.
                shipping = shipping.max(nz(order.getShippingCost()));
                tax = tax.add(nz(order.getTaxAmount()));
                for (OrderItem item : order.getItems()) {
                    merged.addItem(OrderItem.builder()
                            .product(item.getProduct())
                            .productName(item.getProductName())
                            .productSku(item.getProductSku())
                            .productImageUrl(item.getProductImageUrl())
                            .selectedSize(item.getSelectedSize())
                            .selectedColor(item.getSelectedColor())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .discountAmount(item.getDiscountAmount())
                            .subtotal(item.getSubtotal())
                            .build());
                }
            }
            merged.setSubtotal(subtotal);
            merged.setDiscountAmount(discount);
            merged.setOtherCollectionAmount(otherCollection);
            merged.setShippingCost(shipping);
            merged.setTaxAmount(tax);
            merged.calculateTotal();
            merged.setNotes(notes.isEmpty() ? null : String.join(" | ", notes));
            merged.setAdminNotes("Gộp từ: " + String.join(", ", sourceCodes));
            merged.setOrderNumber(nextRegisterOrderCode());

            Order saved = orderRepository.save(merged);
            for (Order order : ordered) {
                order.setStatus(OrderStatus.CANCELLED);
                order.setMergedInto(saved);
                appendAdminNote(order, "Đã gộp vào " + saved.getOrderNumber());
                orderRepository.save(order);
            }
            log.info("Merged {} into {}", sourceCodes, saved.getOrderNumber());

            return ResponseEntity.ok(Map.of(
                    "message", "Đã gộp " + sourceCodes.size() + " đơn thành " + saved.getOrderNumber(),
                    "orderId", saved.getId(),
                    "code", saved.getOrderNumber(),
                    "mergedCodes", sourceCodes));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to merge orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to merge orders"));
        }
    }

    /**
     * The star column.
     * PATCH /store/orders/{orderId}/star
     */
    @PatchMapping("/{orderId}/star")
    public ResponseEntity<?> setStarred(@PathVariable Long orderId, @RequestBody Map<String, Boolean> request) {
        try {
            Order order = findStoreOrder(orderId);
            order.setStarred(Boolean.TRUE.equals(request.get("starred")));
            orderRepository.save(order);
            return ResponseEntity.ok(Map.of("orderId", orderId, "starred", order.getStarred()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to star order: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update order"));
        }
    }

    /**
     * "Cập nhật trạng thái" - ask the carrier where this order's parcel is now.
     * PATCH /store/orders/{orderId}/refresh-delivery
     *
     * The sweep does this every few minutes and the webhook does it the moment
     * anything happens, so this is for the case neither covers: somebody is
     * looking at the order right now and wants the answer without waiting. It
     * ends up in the same place as both.
     */
    @PatchMapping("/{orderId}/refresh-delivery")
    public ResponseEntity<?> refreshDelivery(@PathVariable Long orderId) {
        try {
            Order order = findStoreOrder(orderId);
            Shipment shipment = order.latestShipment();
            if (shipment == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Đơn này chưa có vận đơn nào để cập nhật"));
            }
            OrderStatus before = order.getStatus();
            Shipment refreshed = shipmentService.refreshStatus(shipment.getId());
            String carrierStatus = refreshed.getStatusText() == null ? "" : refreshed.getStatusText();
            Map<String, Object> body = new HashMap<>();
            body.put("orderId", orderId);
            body.put("status", order.getStatus().name());
            body.put("shipmentStatus", carrierStatus);
            body.put("changed", order.getStatus() != before);
            // Nothing moving is a legitimate answer - the parcel may simply not
            // have moved - but it has to be distinguishable from a call that
            // failed, or "I pressed it and nothing happened" means both.
            body.put("message", order.getStatus() != before
                    ? "Đã cập nhật theo hãng vận chuyển."
                    : "Hãng vận chuyển vẫn báo: " + (carrierStatus.isEmpty() ? "chưa có trạng thái" : carrierStatus));
            return ResponseEntity.ok(body);

        } catch (com.ut.edu.backend.shipping.goship.GoshipApiException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to refresh delivery for order {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Không cập nhật được trạng thái vận đơn"));
        }
    }

    /**
     * "Nguoi tao" - only the staff who have actually raised an order here, so
     * the sidebar cannot offer a name that comes back empty.
     * GET /store/orders/creators
     */
    @GetMapping("/creators")
    public ResponseEntity<?> getCreators() {
        Long storeId = tenantGuard.requireStore();
        return ResponseEntity.ok(Map.of("creators", orderRepository.findCreatorUsernames(storeId)));
    }

    /**
     * "Khu vuc giao hang" - the Tinh/TP and Quan/Huyen this store has actually
     * shipped to. Built from the orders themselves rather than from a national
     * address list: a sidebar that offers all 63 provinces to a shop that
     * delivers within one district is a longer list saying less.
     */
    @GetMapping("/delivery-areas")
    public ResponseEntity<?> getDeliveryAreas() {
        Long storeId = tenantGuard.requireStore();
        Map<String, Set<String>> byProvince = new TreeMap<>();
        for (Object[] row : orderRepository.findDeliveryAreas(storeId)) {
            String provinceName = (String) row[0];
            String districtName = (String) row[1];
            byProvince.computeIfAbsent(provinceName, k -> new TreeSet<>());
            if (districtName != null && !districtName.isBlank()) {
                byProvince.get(provinceName).add(districtName);
            }
        }
        List<Map<String, Object>> areas = byProvince.entrySet().stream()
                .map(entry -> Map.<String, Object>of(
                        "province", entry.getKey(),
                        "districts", new ArrayList<>(entry.getValue())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("areas", areas));
    }

    /** Every status a merge may start from - nothing here has taken the customer's money yet. */
    private static final Set<OrderStatus> MERGEABLE_STATUSES = Set.of(
            OrderStatus.PENDING, OrderStatus.PAYMENT_PENDING, OrderStatus.PENDING_COD);

    /** Same per-store DH sequence the register uses (see SaleService), retried the same way. */
    private String nextRegisterOrderCode() {
        Long storeId = tenantGuard.requireStore();
        long count = orderRepository.countByStoreIdAndOrderNumberPrefix(storeId, REGISTER_CODE_PREFIX);
        String code = SequentialCodeGenerator.generate(REGISTER_CODE_PREFIX, count);
        while (orderRepository.existsByStoreIdAndOrderNumber(storeId, code)) {
            code = SequentialCodeGenerator.generate(REGISTER_CODE_PREFIX, ++count);
        }
        return code;
    }

    private static final String REGISTER_CODE_PREFIX = "DH";

    private static void appendAdminNote(Order order, String line) {
        String existing = order.getAdminNotes() == null ? "" : order.getAdminNotes();
        order.setAdminNotes(existing.isEmpty() ? line : existing + "\n" + line);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Shop staff confirming delivery collects the cash exactly as a courier does - one rule, in OrderDeliverySync. */
    private void settleCodOnDelivery(Order order, OrderStatus newStatus) {
        if (newStatus == OrderStatus.DELIVERED) {
            deliverySync.settleCodOnDelivery(order);
        }
    }
}

package com.ut.edu.backend.purchaseorder;

import com.ut.edu.backend.exception.SubscriptionRequiredException;
import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.supplier.Supplier;
import com.ut.edu.backend.user.User;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * "Nhập hàng" (goods receipt) - the real, working slice of KiotViet's "Mua
 * hàng" tab. See PurchaseOrderService for the calculation/transition rules.
 */
@RestController
@RequestMapping("/store/purchase-orders")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@Slf4j
public class PurchaseOrderController {

    private static final List<PurchaseOrderStatus> DEFAULT_STATUSES =
            List.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.COMPLETED);

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private TenantGuard tenantGuard;

    @Autowired
    private SubscriptionGuard subscriptionGuard;

    @Autowired
    private AuthorizationService authorizationService;

    private PurchaseOrder findStorePurchaseOrder(Long id) {
        return purchaseOrderRepository.findById(id)
                .filter(po -> tenantGuard.isCurrentStore(po.getStore()))
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + id));
    }

    /**
     * GET /api/store/purchase-orders?statuses=DRAFT&statuses=COMPLETED&from=2026-09-01&to=2026-09-30&query=PN0001&page=0&size=15
     * Matches KiotViet's Nhập hàng list: Trạng thái checkboxes (default
     * Phiếu tạm + Đã nhập hàng, same as the real screen), a Thời gian range
     * filter "created between", the search box (query, on the code) with the
     * three boxes behind its sliders icon (product / supplier / note), and
     * the two people pickers in the sidebar (createdBy = "Người tạo",
     * completedBy = "Người nhập").
     * Not paginated at the DB level - see the comment below; fine at this
     * app's expected (portfolio-demo) scale.
     */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String supplier,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) String completedBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        try {
            List<PurchaseOrderStatus> statusList = (statuses == null || statuses.isEmpty())
                    ? DEFAULT_STATUSES
                    : statuses.stream().map(PurchaseOrderStatus::valueOf).collect(Collectors.toList());

            Specification<PurchaseOrder> spec = Specification.where(
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
                String like = likePattern(query);
                spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("code")), like));
            }
            if (product != null && !product.isBlank()) {
                String like = likePattern(product);
                spec = spec.and((root, q, cb) -> {
                    // One receipt has many lines, so the join multiplies the
                    // row - distinct keeps a two-line receipt from being
                    // listed twice.
                    q.distinct(true);
                    Join<PurchaseOrder, PurchaseOrderItem> item = root.join("items", JoinType.LEFT);
                    return cb.or(
                            cb.like(cb.lower(item.get("productName")), like),
                            cb.like(cb.lower(item.get("productSku")), like));
                });
            }
            if (supplier != null && !supplier.isBlank()) {
                String like = likePattern(supplier);
                spec = spec.and((root, q, cb) -> {
                    Join<PurchaseOrder, Supplier> ncc = root.join("supplier", JoinType.LEFT);
                    return cb.or(
                            cb.like(cb.lower(ncc.get("name")), like),
                            cb.like(cb.lower(ncc.get("code")), like));
                });
            }
            if (note != null && !note.isBlank()) {
                String like = likePattern(note);
                spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("note")), like));
            }

            // Fetched in full (not Pageable) so the totals row can sum
            // payableAmount across every matching row, not just the current
            // page - simplest correct approach at this app's scale; would
            // need a dedicated aggregate query at real production volume.
            List<PurchaseOrder> matching = purchaseOrderRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));

            // "Người tạo"/"Người nhập" are matched here rather than in the
            // specification: both are joins to users for a plain equality on
            // one column, and the rows are already in memory for the totals.
            List<PurchaseOrder> all = matching.stream()
                    .filter(po -> matchesUser(po.getCreatedBy(), createdBy))
                    .filter(po -> matchesUser(po.getCompletedBy(), completedBy))
                    .collect(Collectors.toList());

            BigDecimal totalPayableAmount = all.stream()
                    .map(PurchaseOrder::getPayableAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int totalItems = all.size();
            int fromIndex = Math.min(page * size, totalItems);
            int toIndex = Math.min(fromIndex + size, totalItems);
            List<PurchaseOrderResponse> pageContent = all.subList(fromIndex, toIndex).stream()
                    .map(PurchaseOrderResponse::summary)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("purchaseOrders", pageContent);
            response.put("currentPage", page);
            response.put("totalItems", totalItems);
            response.put("totalPages", size > 0 ? (int) Math.ceil((double) totalItems / size) : 0);
            response.put("totalPayableAmount", totalPayableAmount);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid filter: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to list purchase orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve purchase orders"));
        }
    }

    private static String likePattern(String raw) {
        return "%" + raw.trim().toLowerCase() + "%";
    }

    /** Absent means "Tất cả" - the picker's own first option, which filters nothing. */
    private static boolean matchesUser(User user, String wanted) {
        if (wanted == null || wanted.isBlank()) {
            return true;
        }
        return user != null && wanted.equals(user.getUsername());
    }

    /**
     * GET /api/store/purchase-orders/people - the two sidebar pickers'
     * options: whoever has actually raised or received a goods receipt, so
     * neither dropdown can offer a name that brings back nothing. Read from
     * every receipt in the store rather than from the filtered set, so the
     * list does not shift as the other filters move.
     */
    @GetMapping("/people")
    public ResponseEntity<?> people() {
        Long storeId = tenantGuard.requireStore();
        return ResponseEntity.ok(Map.of(
                "creators", purchaseOrderRepository.findCreatorUsernames(storeId),
                "receivers", purchaseOrderRepository.findReceiverUsernames(storeId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(PurchaseOrderResponse.detail(findStorePurchaseOrder(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get purchase order: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve purchase order"));
        }
    }

    /** POST /api/store/purchase-orders - always creates a new DRAFT ("Lưu tạm" on a blank form). */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody SavePurchaseOrderRequest request) {
        try {
            Long storeId = tenantGuard.requireStore();
            subscriptionGuard.requireActiveSubscription(storeId);
            User currentUser = authorizationService.getCurrentUser();
            PurchaseOrder saved = purchaseOrderService.create(storeId, currentUser, request);
            log.info("New purchase order created: {} ({} item(s))", saved.getCode(), saved.getItems().size());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Purchase order saved as draft",
                    "purchaseOrder", PurchaseOrderResponse.detail(saved)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create purchase order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create purchase order"));
        }
    }

    /** PUT /api/store/purchase-orders/{id} - "Lưu tạm" on an existing draft. Only DRAFT documents can be edited. */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody SavePurchaseOrderRequest request) {
        PurchaseOrder existing;
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            existing = findStorePurchaseOrder(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        }
        try {
            PurchaseOrder saved = purchaseOrderService.update(existing, request);
            return ResponseEntity.ok(Map.of(
                    "message", "Purchase order updated successfully",
                    "purchaseOrder", PurchaseOrderResponse.detail(saved)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update purchase order: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update purchase order"));
        }
    }

    /** PATCH /api/store/purchase-orders/{id}/complete - "Hoàn thành": locks the document and increments stock. */
    @PatchMapping("/{id}/complete")
    public ResponseEntity<?> complete(@PathVariable Long id) {
        PurchaseOrder po;
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            po = findStorePurchaseOrder(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        }
        try {
            PurchaseOrder saved = purchaseOrderService.complete(po, authorizationService.getCurrentUser());
            return ResponseEntity.ok(Map.of(
                    "message", "Đã hoàn thành phiếu nhập, tồn kho đã được cập nhật",
                    "purchaseOrder", PurchaseOrderResponse.detail(saved)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to complete purchase order: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to complete purchase order"));
        }
    }

    /** PATCH /api/store/purchase-orders/{id}/cancel - "Hủy": abandons a draft, never touches stock. */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id) {
        PurchaseOrder po;
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            po = findStorePurchaseOrder(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        }
        try {
            PurchaseOrder saved = purchaseOrderService.cancel(po);
            return ResponseEntity.ok(Map.of(
                    "message", "Đã hủy phiếu nhập",
                    "purchaseOrder", PurchaseOrderResponse.detail(saved)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to cancel purchase order: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to cancel purchase order"));
        }
    }

    /**
     * PATCH /api/store/purchase-orders/{id}/star - "Đánh dấu", the star column
     * on the list. A bookmark only: it never touches stock, money or status,
     * so unlike every other write here it is not behind the subscription
     * guard - the same treatment the orders list gives its own star.
     */
    @PatchMapping("/{id}/star")
    public ResponseEntity<?> setStarred(@PathVariable Long id, @RequestBody Map<String, Boolean> request) {
        try {
            PurchaseOrder po = findStorePurchaseOrder(id);
            po.setStarred(Boolean.TRUE.equals(request.get("starred")));
            purchaseOrderRepository.save(po);
            return ResponseEntity.ok(Map.of("id", id, "starred", po.getStarred()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to star purchase order: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update purchase order"));
        }
    }
}

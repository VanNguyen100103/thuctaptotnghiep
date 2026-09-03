package com.ut.edu.backend.purchaseorder;

import com.ut.edu.backend.exception.SubscriptionRequiredException;
import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.user.User;

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
     * Phiếu tạm + Đã nhập hàng, same as the real screen) and a Thời gian
     * range filter "created between". Not paginated at the DB level - see
     * the comment below; fine at this app's expected (portfolio-demo) scale.
     */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String query,
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
                String like = "%" + query.trim().toLowerCase() + "%";
                spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("code")), like));
            }

            // Fetched in full (not Pageable) so the totals row can sum
            // payableAmount across every matching row, not just the current
            // page - simplest correct approach at this app's scale; would
            // need a dedicated aggregate query at real production volume.
            List<PurchaseOrder> all = purchaseOrderRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
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
}

package com.ut.edu.backend.purchasereturn;

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
 * "Trả hàng nhập" - the other half of Mua hàng's stock movement. Endpoint for
 * endpoint the same shape as PurchaseOrderController, because the list screen
 * behind it is the same screen with the money columns pointing the other way.
 */
@RestController
@RequestMapping("/store/purchase-returns")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@Slf4j
public class PurchaseReturnController {

    private static final List<PurchaseReturnStatus> DEFAULT_STATUSES =
            List.of(PurchaseReturnStatus.DRAFT, PurchaseReturnStatus.COMPLETED);

    @Autowired
    private PurchaseReturnRepository purchaseReturnRepository;

    @Autowired
    private PurchaseReturnService purchaseReturnService;

    @Autowired
    private TenantGuard tenantGuard;

    @Autowired
    private SubscriptionGuard subscriptionGuard;

    @Autowired
    private AuthorizationService authorizationService;

    private PurchaseReturn findStorePurchaseReturn(Long id) {
        return purchaseReturnRepository.findById(id)
                .filter(pr -> tenantGuard.isCurrentStore(pr.getStore()))
                .orElseThrow(() -> new IllegalArgumentException("Purchase return not found: " + id));
    }

    /**
     * GET /api/store/purchase-returns?statuses=DRAFT&statuses=COMPLETED&from=...&query=THN0001&page=0&size=15
     *
     * Same filters as the Nhập hàng list - Trạng thái checkboxes (default
     * Phiếu tạm + Đã trả hàng), a created-between range, the search box on
     * the code plus the three boxes behind its sliders icon, and the two
     * people pickers. Fetched in full rather than by Pageable so the totals
     * row can sum every matching row; see PurchaseOrderController#list for
     * the same note.
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
            List<PurchaseReturnStatus> statusList = (statuses == null || statuses.isEmpty())
                    ? DEFAULT_STATUSES
                    : statuses.stream().map(PurchaseReturnStatus::valueOf).collect(Collectors.toList());

            Specification<PurchaseReturn> spec = Specification.where(
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
                    // The join multiplies the row, so a two-line return would
                    // otherwise be listed twice.
                    q.distinct(true);
                    Join<PurchaseReturn, PurchaseReturnItem> item = root.join("items", JoinType.LEFT);
                    return cb.or(
                            cb.like(cb.lower(item.get("productName")), like),
                            cb.like(cb.lower(item.get("productSku")), like));
                });
            }
            if (supplier != null && !supplier.isBlank()) {
                String like = likePattern(supplier);
                spec = spec.and((root, q, cb) -> {
                    Join<PurchaseReturn, Supplier> ncc = root.join("supplier", JoinType.LEFT);
                    return cb.or(
                            cb.like(cb.lower(ncc.get("name")), like),
                            cb.like(cb.lower(ncc.get("code")), like));
                });
            }
            if (note != null && !note.isBlank()) {
                String like = likePattern(note);
                spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("note")), like));
            }

            List<PurchaseReturn> matching = purchaseReturnRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));

            List<PurchaseReturn> all = matching.stream()
                    .filter(pr -> matchesUser(pr.getCreatedBy(), createdBy))
                    .filter(pr -> matchesUser(pr.getCompletedBy(), completedBy))
                    .collect(Collectors.toList());

            BigDecimal totalRefundAmount = all.stream()
                    .map(PurchaseReturn::getRefundAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int totalItems = all.size();
            int fromIndex = Math.min(Math.max(page, 0) * size, totalItems);
            int toIndex = Math.min(fromIndex + size, totalItems);
            List<PurchaseReturnResponse> pageContent = all.subList(fromIndex, toIndex).stream()
                    .map(PurchaseReturnResponse::summary)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("purchaseReturns", pageContent);
            response.put("currentPage", page);
            response.put("totalItems", totalItems);
            response.put("totalPages", size > 0 ? (int) Math.ceil((double) totalItems / size) : 0);
            response.put("totalRefundAmount", totalRefundAmount);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid filter: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to list purchase returns", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve purchase returns"));
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

    /** GET /api/store/purchase-returns/people - the sidebar pickers' options: whoever has actually raised or completed a return. */
    @GetMapping("/people")
    public ResponseEntity<?> people() {
        Long storeId = tenantGuard.requireStore();
        return ResponseEntity.ok(Map.of(
                "creators", purchaseReturnRepository.findCreatorUsernames(storeId),
                "receivers", purchaseReturnRepository.findReceiverUsernames(storeId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(PurchaseReturnResponse.detail(findStorePurchaseReturn(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get purchase return: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve purchase return"));
        }
    }

    /** POST /api/store/purchase-returns - always creates a new DRAFT ("Lưu tạm" on a blank form). */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody SavePurchaseReturnRequest request) {
        try {
            Long storeId = tenantGuard.requireStore();
            subscriptionGuard.requireActiveSubscription(storeId);
            User currentUser = authorizationService.getCurrentUser();
            PurchaseReturn saved = purchaseReturnService.create(storeId, currentUser, request);
            log.info("New purchase return created: {} ({} line(s))", saved.getCode(), saved.getItems().size());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Purchase return saved as draft",
                    "purchaseReturn", PurchaseReturnResponse.detail(saved)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create purchase return", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create purchase return"));
        }
    }

    /** PUT /api/store/purchase-returns/{id} - "Lưu tạm" on an existing draft. Only DRAFT documents can be edited. */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody SavePurchaseReturnRequest request) {
        PurchaseReturn existing;
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            existing = findStorePurchaseReturn(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        }
        try {
            PurchaseReturn saved = purchaseReturnService.update(existing, request);
            return ResponseEntity.ok(Map.of(
                    "message", "Purchase return updated successfully",
                    "purchaseReturn", PurchaseReturnResponse.detail(saved)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update purchase return: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update purchase return"));
        }
    }

    /** PATCH /api/store/purchase-returns/{id}/complete - "Hoàn thành": locks the document and decrements stock. */
    @PatchMapping("/{id}/complete")
    public ResponseEntity<?> complete(@PathVariable Long id) {
        PurchaseReturn pr;
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            pr = findStorePurchaseReturn(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        }
        try {
            PurchaseReturn saved = purchaseReturnService.complete(pr, authorizationService.getCurrentUser());
            return ResponseEntity.ok(Map.of(
                    "message", "Đã hoàn thành phiếu trả hàng, tồn kho đã được cập nhật",
                    "purchaseReturn", PurchaseReturnResponse.detail(saved)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to complete purchase return: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to complete purchase return"));
        }
    }

    /** PATCH /api/store/purchase-returns/{id}/cancel - "Hủy": abandons a draft, never touches stock. */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id) {
        PurchaseReturn pr;
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            pr = findStorePurchaseReturn(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        }
        try {
            PurchaseReturn saved = purchaseReturnService.cancel(pr);
            return ResponseEntity.ok(Map.of(
                    "message", "Đã hủy phiếu trả hàng",
                    "purchaseReturn", PurchaseReturnResponse.detail(saved)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to cancel purchase return: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to cancel purchase return"));
        }
    }

    /** PATCH /api/store/purchase-returns/{id}/star - "Đánh dấu". A bookmark only, so it stays outside the subscription guard like the receipt list's own star. */
    @PatchMapping("/{id}/star")
    public ResponseEntity<?> setStarred(@PathVariable Long id, @RequestBody Map<String, Boolean> request) {
        try {
            PurchaseReturn pr = findStorePurchaseReturn(id);
            pr.setStarred(Boolean.TRUE.equals(request.get("starred")));
            purchaseReturnRepository.save(pr);
            return ResponseEntity.ok(Map.of("id", id, "starred", pr.getStarred()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to star purchase return: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update purchase return"));
        }
    }
}

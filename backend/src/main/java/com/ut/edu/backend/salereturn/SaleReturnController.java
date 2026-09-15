package com.ut.edu.backend.salereturn;

import com.ut.edu.backend.exception.SubscriptionRequiredException;
import com.ut.edu.backend.sale.Customer;
import com.ut.edu.backend.sale.Sale;
import com.ut.edu.backend.sale.SalePaymentMethod;
import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * "Trả hàng" - the Đơn hàng menu entry beside Hóa đơn, and the other side of
 * the same counter. Shaped like SaleController, because the screen behind it
 * is the invoice list with the money pointing the other way: no status
 * filters, because a return has no status (see SaleReturn), and no draft
 * endpoints, because there is no draft.
 */
@RestController
@RequestMapping("/store/sale-returns")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@Slf4j
public class SaleReturnController {

    @Autowired
    private SaleReturnRepository saleReturnRepository;

    @Autowired
    private SaleReturnService saleReturnService;

    @Autowired
    private TenantGuard tenantGuard;

    @Autowired
    private SubscriptionGuard subscriptionGuard;

    @Autowired
    private AuthorizationService authorizationService;

    private SaleReturn findStoreSaleReturn(Long id) {
        return saleReturnRepository.findById(id)
                .filter(sr -> tenantGuard.isCurrentStore(sr.getStore()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu trả hàng: " + id));
    }

    /**
     * GET /api/store/sale-returns?from=...&query=TH0001&refundMethods=CASH&page=0&size=15
     *
     * Fetched in full rather than by Pageable so the totals row can sum every
     * matching return rather than the page on screen - the same trade the
     * Hóa đơn and Nhập hàng lists make, for the same reason.
     */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String saleCode,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) List<String> refundMethods,
            @RequestParam(required = false) String createdBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        try {
            Specification<SaleReturn> spec = Specification.where(null);
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
            if (saleCode != null && !saleCode.isBlank()) {
                String like = likePattern(saleCode);
                spec = spec.and((root, q, cb) -> {
                    Join<SaleReturn, Sale> sale = root.join("sale", JoinType.INNER);
                    return cb.like(cb.lower(sale.get("code")), like);
                });
            }
            if (customer != null && !customer.isBlank()) {
                String like = likePattern(customer);
                // An inner join, so a walk-in return drops out - which is what
                // searching by customer means.
                spec = spec.and((root, q, cb) -> {
                    Join<SaleReturn, Customer> c = root.join("customer", JoinType.INNER);
                    return cb.or(cb.like(cb.lower(c.get("code")), like),
                            cb.like(cb.lower(c.get("name")), like),
                            cb.like(cb.lower(c.get("phone")), like));
                });
            }
            if (product != null && !product.isBlank()) {
                String like = likePattern(product);
                // Reads the line snapshot columns, not the product they point
                // at: a return of a since-deleted product is still findable by
                // the name it was sold under. distinct(), or the join would
                // list a two-line return twice.
                spec = spec.and((root, q, cb) -> {
                    q.distinct(true);
                    Join<SaleReturn, SaleReturnItem> line = root.join("items", JoinType.INNER);
                    return cb.or(cb.like(cb.lower(line.get("productName")), like),
                            cb.like(cb.lower(line.get("productSku")), like));
                });
            }
            if (note != null && !note.isBlank()) {
                String like = likePattern(note);
                spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("note")), like));
            }
            if (refundMethods != null && !refundMethods.isEmpty()) {
                List<SalePaymentMethod> methods = refundMethods.stream()
                        .map(SalePaymentMethod::valueOf)
                        .collect(Collectors.toList());
                spec = spec.and((root, q, cb) -> root.get("refundMethod").in(methods));
            }
            if (createdBy != null && !createdBy.isBlank()) {
                String username = createdBy.trim();
                spec = spec.and((root, q, cb) -> cb.equal(root.join("createdBy").get("username"), username));
            }

            List<SaleReturn> all = saleReturnRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));

            BigDecimal totalGoodsValue = all.stream()
                    .map(SaleReturn::getTotalGoodsValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalRefundAmount = all.stream()
                    .map(SaleReturn::getRefundAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int totalItems = all.size();
            int fromIndex = Math.min(Math.max(page, 0) * size, totalItems);
            int toIndex = Math.min(fromIndex + size, totalItems);
            List<SaleReturnResponse> pageContent = all.subList(fromIndex, toIndex).stream()
                    .map(SaleReturnResponse::summary)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("saleReturns", pageContent);
            response.put("currentPage", page);
            response.put("totalItems", totalItems);
            response.put("totalPages", size > 0 ? (int) Math.ceil((double) totalItems / size) : 0);
            response.put("totalGoodsValue", totalGoodsValue);
            response.put("totalRefundAmount", totalRefundAmount);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid filter: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to list sale returns", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve sale returns"));
        }
    }

    private static String likePattern(String raw) {
        return "%" + raw.trim().toLowerCase() + "%";
    }

    /** GET /api/store/sale-returns/creators - "Người trả hàng" options: only whoever has actually run a return. */
    @GetMapping("/creators")
    public ResponseEntity<?> creators() {
        Long storeId = tenantGuard.requireStore();
        return ResponseEntity.ok(Map.of("creators", saleReturnRepository.findCreatorUsernames(storeId)));
    }

    /**
     * GET /api/store/sale-returns/returnable/{saleId} - what the "Trả hàng"
     * form opens on: the invoice, its tenders, and how many units of each line
     * are still returnable.
     */
    @GetMapping("/returnable/{saleId}")
    public ResponseEntity<?> returnable(@PathVariable Long saleId) {
        try {
            return ResponseEntity.ok(saleReturnService.findReturnable(saleId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to load returnable invoice: {}", saleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve invoice"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(SaleReturnResponse.detail(findStoreSaleReturn(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get sale return: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve sale return"));
        }
    }

    /**
     * POST /api/store/sale-returns - "Trả hàng": writes the document, puts the
     * goods back and names the refund, all in one call. There is no draft step
     * to save first (see SaleReturn).
     */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateSaleReturnRequest request) {
        try {
            Long storeId = tenantGuard.requireStore();
            subscriptionGuard.requireActiveSubscription(storeId);
            User cashier = authorizationService.getCurrentUser();
            SaleReturn saved = saleReturnService.create(storeId, cashier, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Đã trả hàng, tồn kho đã được cập nhật",
                    "saleReturn", SaleReturnResponse.detail(saved)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create sale return", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create sale return"));
        }
    }
}

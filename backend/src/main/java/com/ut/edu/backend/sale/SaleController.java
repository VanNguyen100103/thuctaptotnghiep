package com.ut.edu.backend.sale;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * "Bán hàng" - the POS register. See SaleService for the checkout rules.
 */
@RestController
@RequestMapping("/store/sales")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@Slf4j
public class SaleController {

    @Autowired
    private SaleService saleService;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private TenantGuard tenantGuard;

    @Autowired
    private SubscriptionGuard subscriptionGuard;

    @Autowired
    private AuthorizationService authorizationService;

    /**
     * GET /api/store/sales?from=2026-09-01&to=2026-09-30&query=HD0001&page=0&size=15
     *
     * Backs the dashboard's "Hóa đơn" list - every sale the register has run,
     * newest first, filtered by date range and invoice code. Shaped like
     * PurchaseOrderController.list, totals included: the whole match set is
     * loaded so the totals row sums every matching invoice rather than the
     * page on screen.
     */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) List<String> paymentMethods,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        try {
            Specification<Sale> spec = Specification.where(null);
            if (paymentMethods != null && !paymentMethods.isEmpty()) {
                List<SalePaymentMethod> methods = paymentMethods.stream()
                        .map(SalePaymentMethod::valueOf)
                        .collect(Collectors.toList());
                // An invoice can be split across tenders, so this matches "paid
                // with at least one of these" - and distinct(), or a split
                // invoice would come back once per matching tender.
                spec = spec.and((root, q, cb) -> {
                    q.distinct(true);
                    return root.join("payments").get("method").in(methods);
                });
            }
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
            if (product != null && !product.isBlank()) {
                String like = "%" + product.trim().toLowerCase() + "%";
                // Reads the line's snapshot columns, not the product it points
                // at: a sale of a since-deleted product is still findable by the
                // name it was sold under.
                spec = spec.and((root, q, cb) -> {
                    q.distinct(true);
                    var line = root.join("items");
                    return cb.or(cb.like(cb.lower(line.get("productName")), like),
                            cb.like(cb.lower(line.get("productSku")), like));
                });
            }
            if (customer != null && !customer.isBlank()) {
                String like = "%" + customer.trim().toLowerCase() + "%";
                // An inner join, so a walk-in sale with no customer attached
                // drops out - which is what searching by customer means.
                spec = spec.and((root, q, cb) -> {
                    var c = root.join("customer");
                    return cb.or(cb.like(cb.lower(c.get("code")), like),
                            cb.like(cb.lower(c.get("name")), like),
                            cb.like(cb.lower(c.get("phone")), like));
                });
            }
            if (note != null && !note.isBlank()) {
                String like = "%" + note.trim().toLowerCase() + "%";
                spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("note")), like));
            }

            List<Sale> all = saleRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
            List<SaleResponse> summaries = all.stream().map(SaleResponse::summary).collect(Collectors.toList());

            BigDecimal totalAmount = summaries.stream()
                    .map(SaleResponse::totalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalReceived = summaries.stream()
                    .map(SaleResponse::amountReceived)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int totalItems = summaries.size();
            int fromIndex = Math.min(page * size, totalItems);
            int toIndex = Math.min(fromIndex + size, totalItems);

            Map<String, Object> response = new HashMap<>();
            response.put("sales", summaries.subList(fromIndex, toIndex));
            response.put("currentPage", page);
            response.put("totalItems", totalItems);
            response.put("totalPages", size > 0 ? (int) Math.ceil((double) totalItems / size) : 0);
            response.put("totalAmount", totalAmount);
            response.put("totalReceived", totalReceived);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid filter: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to list sales", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve sales"));
        }
    }

    /**
     * GET /api/store/sales/{id} - one invoice with its lines and tenders.
     * findById bypasses the tenant filter, so the store check is explicit
     * here: another store's invoice must look missing, not forbidden.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return saleRepository.findById(id)
                .filter(sale -> tenantGuard.isCurrentStore(sale.getStore()))
                .<ResponseEntity<?>>map(sale -> ResponseEntity.ok(SaleResponse.from(sale)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Không tìm thấy hóa đơn: " + id)));
    }

    /** POST /api/store/sales - "Thanh toán": finalizes the sale immediately (no draft step). */
    @PostMapping
    public ResponseEntity<?> checkout(@Valid @RequestBody CreateSaleRequest request) {
        try {
            Long storeId = tenantGuard.requireStore();
            subscriptionGuard.requireActiveSubscription(storeId);
            User cashier = authorizationService.getCurrentUser();
            Sale saved = saleService.checkout(storeId, cashier, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Thanh toán thành công",
                    "sale", SaleResponse.from(saved)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to checkout sale", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to complete sale"));
        }
    }
}

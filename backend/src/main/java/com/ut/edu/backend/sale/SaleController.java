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
            @RequestParam(required = false) String einvoiceNumber,
            @RequestParam(required = false) String trackingCode,
            @RequestParam(required = false) String orderCode,
            @RequestParam(required = false) String itemNote,
            @RequestParam(required = false) List<String> paymentMethods,
            @RequestParam(required = false) List<String> invoiceTypes,
            @RequestParam(required = false) List<String> invoiceStatuses,
            @RequestParam(required = false) List<String> einvoiceStatuses,
            @RequestParam(required = false) List<String> deliveryStatuses,
            @RequestParam(required = false) List<String> deliveryPartners,
            @RequestParam(required = false) String seller,
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
            if (seller != null && !seller.isBlank()) {
                // "Người bán" - who ran the register. The one of KiotViet's
                // people filters this store actually records on an invoice.
                String username = seller.trim();
                spec = spec.and((root, q, cb) -> cb.equal(root.join("createdBy").get("username"), username));
            }

            /*
             * The rest of KiotViet's invoice filters ask about things a POS
             * sale does not have. Checkout is atomic and the goods go over the
             * counter (see Sale), so every invoice here is "Không giao hàng"
             * and "Hoàn thành", carries no e-invoice and no shipment. Asking
             * for any other value therefore matches no invoice - the same
             * empty list KiotViet gives a shop that has never shipped an
             * order. The day Sale grows a status or a shipment these become
             * ordinary predicates, which is why they are filters here rather
             * than controls the UI leaves dead.
             */
            spec = narrowByConstantFacet(spec, invoiceTypes, "NONE");
            spec = narrowByConstantFacet(spec, invoiceStatuses, "COMPLETED");
            spec = narrowByConstantFacet(spec, einvoiceStatuses, "NOT_ISSUED");
            spec = narrowByConstantFacet(spec, deliveryStatuses, null);
            spec = narrowByConstantFacet(spec, deliveryPartners, null);
            spec = narrowByAbsentField(spec, einvoiceNumber);
            spec = narrowByAbsentField(spec, trackingCode);
            spec = narrowByAbsentField(spec, orderCode);
            spec = narrowByAbsentField(spec, itemNote);

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
     * Narrows by a facet every POS invoice shares one single value of. An
     * empty selection filters nothing; a selection containing
     * {@code sharedValue} matches every invoice; anything else matches none.
     * {@code sharedValue} is null for a facet no invoice has any value of.
     */
    private static Specification<Sale> narrowByConstantFacet(
            Specification<Sale> spec, List<String> selected, String sharedValue) {
        if (selected == null || selected.isEmpty() || (sharedValue != null && selected.contains(sharedValue))) {
            return spec;
        }
        return spec.and((root, q, cb) -> cb.disjunction());
    }

    /** The advanced-search boxes for fields no POS invoice carries - a filled one matches nothing. */
    private static Specification<Sale> narrowByAbsentField(Specification<Sale> spec, String term) {
        return term == null || term.isBlank() ? spec : spec.and((root, q, cb) -> cb.disjunction());
    }

    /**
     * GET /api/store/sales/sellers - the "Người bán" options: whoever has
     * actually run the register, rather than every user on the store, so the
     * dropdown only offers names that can bring back an invoice.
     */
    @GetMapping("/sellers")
    public ResponseEntity<?> sellers() {
        Long storeId = tenantGuard.requireStore();
        return ResponseEntity.ok(Map.of("sellers", saleRepository.findSellerUsernames(storeId)));
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
            SaleService.CheckoutResult result = saleService.checkout(storeId, cashier, request);
            Map<String, Object> body = new HashMap<>();
            body.put("message", "Thanh toán thành công");
            body.put("sale", SaleResponse.from(result.sale()));
            // "Bán giao hàng" also raised an order; the register books the
            // parcel next and has to be able to say which order it carries.
            if (result.order() != null) {
                body.put("orderId", result.order().getId());
                body.put("orderCode", result.order().getOrderNumber());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
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

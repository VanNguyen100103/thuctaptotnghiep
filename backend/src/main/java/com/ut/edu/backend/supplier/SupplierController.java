package com.ut.edu.backend.supplier;

import com.ut.edu.backend.common.SequentialCodeGenerator;
import com.ut.edu.backend.purchaseorder.PurchaseOrderRepository;
import com.ut.edu.backend.purchaseorder.PurchaseOrderStatus;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * "Nhà cung cấp" - the supplier screen under the Mua hàng tab, and the
 * supplier search + quick-add behind the Nhập hàng form.
 *
 * The list is the same shape as KiotViet's own: a sidebar of filters (nhóm,
 * Tổng mua, Nợ hiện tại, Trạng thái), a search box, and two money columns
 * rolled up from the store's goods receipts. Filtering and paging happen in
 * memory here rather than in SQL - suppliers number in the tens/low hundreds
 * per store, and the totals row has to sum every matching supplier rather
 * than the page on screen, the same trade-off PurchaseOrderController#list
 * documents.
 */
@RestController
@RequestMapping("/store/suppliers")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@Slf4j
public class SupplierController {

    private static final String CODE_PREFIX = "NCC";
    private static final int MAX_CODE_RETRIES = 5;

    /** "Toàn thời gian" for the Tổng mua range - the widest pair a timestamp column can hold here. */
    private static final LocalDateTime TIME_FLOOR = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime TIME_CEILING = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private TenantGuard tenantGuard;

    @Autowired
    private SubscriptionGuard subscriptionGuard;

    private Supplier findStoreSupplier(Long id) {
        return supplierRepository.findById(id)
                .filter(s -> tenantGuard.isCurrentStore(s.getStore()))
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + id));
    }

    /**
     * GET /api/store/suppliers?query=...&status=active&group=...&totalFrom=...&debtTo=...&from=2026-09-01&page=0&size=15
     *
     * Serves two callers: the Nhà cung cấp list with every filter its sidebar
     * has, and the Nhập hàng form's "Tìm nhà cung cấp" box, which passes just
     * a query and reads the first page of active suppliers back.
     */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) String group,
            @RequestParam(defaultValue = "active") String status,
            @RequestParam(required = false) BigDecimal totalFrom,
            @RequestParam(required = false) BigDecimal totalTo,
            @RequestParam(required = false) BigDecimal debtFrom,
            @RequestParam(required = false) BigDecimal debtTo,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        try {
            Long storeId = tenantGuard.requireStore();

            // "Thời gian" sits inside the sidebar's Tổng mua block and narrows
            // that column only - see PurchaseOrderRepository#sumDebtBySupplier
            // for why the debt column ignores it.
            LocalDateTime rangeFrom = (from == null || from.isBlank())
                    ? TIME_FLOOR
                    : LocalDate.parse(from).atStartOfDay();
            LocalDateTime rangeTo = (to == null || to.isBlank())
                    ? TIME_CEILING
                    : LocalDate.parse(to).atTime(LocalTime.MAX);

            Map<Long, BigDecimal> purchased = amountsBySupplier(
                    purchaseOrderRepository.sumPurchasedBySupplier(
                            storeId, PurchaseOrderStatus.COMPLETED, rangeFrom, rangeTo));
            Map<Long, BigDecimal> debts = amountsBySupplier(
                    purchaseOrderRepository.sumDebtBySupplier(storeId, PurchaseOrderStatus.COMPLETED));

            List<SupplierResponse> matching = supplierRepository.findAllByOrderByNameAsc().stream()
                    .filter(s -> matchesStatus(s, status))
                    .filter(s -> matchesGroup(s, group))
                    .filter(s -> matchesKeyword(s, query))
                    .filter(s -> contains(s.getPhone(), phone))
                    .filter(s -> contains(s.getNote(), note))
                    .map(s -> SupplierResponse.of(
                            s,
                            purchased.getOrDefault(s.getId(), BigDecimal.ZERO),
                            debts.getOrDefault(s.getId(), BigDecimal.ZERO)))
                    .filter(s -> inRange(s.totalPurchase(), totalFrom, totalTo))
                    .filter(s -> inRange(s.currentDebt(), debtFrom, debtTo))
                    .sorted(Comparator.comparing(SupplierResponse::name, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());

            BigDecimal totalPurchaseSum = matching.stream()
                    .map(SupplierResponse::totalPurchase)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalDebtSum = matching.stream()
                    .map(SupplierResponse::currentDebt)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int totalItems = matching.size();
            int fromIndex = Math.min(Math.max(page, 0) * size, totalItems);
            int toIndex = Math.min(fromIndex + size, totalItems);

            Map<String, Object> response = new HashMap<>();
            response.put("suppliers", matching.subList(fromIndex, toIndex));
            response.put("currentPage", page);
            response.put("totalItems", totalItems);
            response.put("totalPages", size > 0 ? (int) Math.ceil((double) totalItems / size) : 0);
            response.put("totalPurchaseSum", totalPurchaseSum);
            response.put("totalDebtSum", totalDebtSum);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid filter: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to list suppliers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve suppliers"));
        }
    }

    /** GET /api/store/suppliers/groups - the sidebar's "Nhóm nhà cung cấp" options, whatever the shop has actually typed. */
    @GetMapping("/groups")
    public ResponseEntity<?> groups() {
        return ResponseEntity.ok(Map.of("groups", supplierRepository.findGroupNames(tenantGuard.requireStore())));
    }

    private static Map<Long, BigDecimal> amountsBySupplier(List<PurchaseOrderRepository.SupplierAmount> rows) {
        return rows.stream().collect(Collectors.toMap(
                PurchaseOrderRepository.SupplierAmount::getSupplierId,
                row -> row.getAmount() == null ? BigDecimal.ZERO : row.getAmount(),
                (a, b) -> a.add(b)));
    }

    /** KiotViet's three pills: "Đang hoạt động" (their default), "Ngừng hoạt động", "Tất cả". */
    private static boolean matchesStatus(Supplier supplier, String status) {
        if ("all".equalsIgnoreCase(status)) {
            return true;
        }
        boolean active = Boolean.TRUE.equals(supplier.getActive());
        return "inactive".equalsIgnoreCase(status) ? !active : active;
    }

    private static boolean matchesGroup(Supplier supplier, String group) {
        return group == null || group.isBlank() || group.equalsIgnoreCase(supplier.getGroupName());
    }

    /** The search box itself: mã, tên or điện thoại, matching the diacritic-insensitive behaviour of SupplierRepository#search. */
    private static boolean matchesKeyword(Supplier supplier, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String needle = unaccentLower(keyword.trim());
        return unaccentLower(supplier.getCode()).contains(needle)
                || unaccentLower(supplier.getName()).contains(needle)
                || unaccentLower(supplier.getPhone()).contains(needle);
    }

    private static boolean contains(String field, String needle) {
        return needle == null || needle.isBlank() || unaccentLower(field).contains(unaccentLower(needle.trim()));
    }

    /** Both ends optional - an empty box on either side of "Từ ... Tới ..." means unbounded, not zero. */
    private static boolean inRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min != null && value.compareTo(min) < 0) {
            return false;
        }
        return max == null || value.compareTo(max) <= 0;
    }

    /** Same NFD diacritic-stripping as SlugUtil, so "nuoc" finds "Nước sạch" the way the native search query does. */
    private static String unaccentLower(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[đĐ]", "d")
                .toLowerCase();
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody SupplierRequest request) {
        try {
            Long storeId = tenantGuard.requireStore();
            subscriptionGuard.requireActiveSubscription(storeId);

            Supplier supplier = Supplier.builder()
                    .store(tenantGuard.currentStoreRef())
                    .name(request.name().trim())
                    .phone(request.phone())
                    .email(request.email())
                    .address(request.address())
                    .region(request.region())
                    .ward(request.ward())
                    .groupName(request.groupName())
                    .taxCode(request.taxCode())
                    .companyName(request.companyName())
                    .note(request.note())
                    .active(true)
                    .build();

            // Retry on the rare race where two requests generate the same
            // next-in-sequence code concurrently (same pattern as PurchaseOrderService).
            DataIntegrityViolationException lastError = null;
            for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
                supplier.setCode(SequentialCodeGenerator.generate(CODE_PREFIX, supplierRepository.countByStoreId(storeId) + attempt));
                try {
                    Supplier saved = supplierRepository.save(supplier);
                    log.info("New supplier created: {} (code {})", saved.getName(), saved.getCode());
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(Map.of("message", "Supplier created successfully", "supplier", SupplierResponse.of(saved)));
                } catch (DataIntegrityViolationException e) {
                    lastError = e;
                }
            }
            throw lastError;

        } catch (jakarta.validation.ConstraintViolationException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (com.ut.edu.backend.exception.SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create supplier", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create supplier"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            Supplier supplier = findStoreSupplier(id);
            supplier.setName(request.name().trim());
            supplier.setPhone(request.phone());
            supplier.setEmail(request.email());
            supplier.setAddress(request.address());
            supplier.setRegion(request.region());
            supplier.setWard(request.ward());
            supplier.setGroupName(request.groupName());
            supplier.setTaxCode(request.taxCode());
            supplier.setCompanyName(request.companyName());
            supplier.setNote(request.note());
            Supplier saved = supplierRepository.save(supplier);
            return ResponseEntity.ok(Map.of("message", "Supplier updated successfully", "supplier", SupplierResponse.of(saved)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (com.ut.edu.backend.exception.SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update supplier: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update supplier"));
        }
    }

    /**
     * PATCH /api/store/suppliers/{id}/active - "Ngừng hoạt động" and its
     * undo. A stopped supplier keeps every receipt it appears on and can be
     * brought back; it just stops being offered on the Nhập hàng form and
     * drops out of the list's default "Đang hoạt động" filter.
     */
    @PatchMapping("/{id}/active")
    public ResponseEntity<?> setActive(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            Supplier supplier = findStoreSupplier(id);
            supplier.setActive(Boolean.parseBoolean(String.valueOf(body.get("active"))));
            Supplier saved = supplierRepository.save(supplier);
            return ResponseEntity.ok(Map.of(
                    "message", Boolean.TRUE.equals(saved.getActive())
                            ? "Supplier activated successfully"
                            : "Supplier deactivated successfully",
                    "supplier", SupplierResponse.of(saved)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (com.ut.edu.backend.exception.SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to change supplier status: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update supplier"));
        }
    }

    /**
     * "Xóa" - a real delete, not the soft one this used to do: with a
     * Trạng thái filter on the list, flipping active=false is what
     * "Ngừng hoạt động" means, and two buttons doing the same thing is one
     * button lying. A supplier that already appears on a goods receipt is
     * refused instead, because deleting it would take the receipt's own
     * record of who the goods came from with it.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            Supplier supplier = findStoreSupplier(id);
            long receipts = purchaseOrderRepository.countBySupplierId(id);
            if (receipts > 0) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "Nhà cung cấp đã có " + receipts + " phiếu nhập, không xóa được. "
                                + "Dùng \"Ngừng hoạt động\" để ẩn khỏi danh sách."));
            }
            supplierRepository.delete(supplier);
            return ResponseEntity.ok(Map.of("message", "Supplier deleted successfully", "supplierId", id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (com.ut.edu.backend.exception.SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete supplier: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete supplier"));
        }
    }
}

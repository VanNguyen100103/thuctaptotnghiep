package com.ut.edu.backend.supplier;

import com.ut.edu.backend.common.SequentialCodeGenerator;
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

import java.util.List;
import java.util.Map;

/**
 * "Nhà cung cấp" - minimal supplier management backing the Nhập hàng
 * (PurchaseOrder) form's supplier search + quick-add. No dedicated list page
 * with filters/paging like Hàng hóa - suppliers are expected to number in
 * the tens/low hundreds per store, so a plain name-sorted list is enough.
 */
@RestController
@RequestMapping("/store/suppliers")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@Slf4j
public class SupplierController {

    private static final String CODE_PREFIX = "NCC";
    private static final int MAX_CODE_RETRIES = 5;

    @Autowired
    private SupplierRepository supplierRepository;

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
     * GET /api/store/suppliers?query=... - full active list (optionally
     * filtered), used both by the standalone supplier list and the Nhập
     * hàng form's "Tìm nhà cung cấp" search box.
     */
    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String query) {
        try {
            List<Supplier> suppliers = (query != null && !query.trim().isBlank())
                    ? supplierRepository.search(query.trim(), tenantGuard.requireStore())
                    : supplierRepository.findByActiveTrueOrderByNameAsc();
            return ResponseEntity.ok(Map.of("suppliers", suppliers));
        } catch (Exception e) {
            log.error("Failed to list suppliers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve suppliers"));
        }
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
                    .taxCode(request.taxCode())
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
                            .body(Map.of("message", "Supplier created successfully", "supplier", saved));
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
            supplier.setTaxCode(request.taxCode());
            supplier.setNote(request.note());
            Supplier saved = supplierRepository.save(supplier);
            return ResponseEntity.ok(Map.of("message", "Supplier updated successfully", "supplier", saved));
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

    /** Soft delete (active=false), same convention as Product/Category. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            Supplier supplier = findStoreSupplier(id);
            supplier.setActive(false);
            supplierRepository.save(supplier);
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

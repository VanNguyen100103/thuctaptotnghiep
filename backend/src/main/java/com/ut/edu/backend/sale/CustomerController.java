package com.ut.edu.backend.sale;

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
 * "Khách hàng" - minimal customer management backing the Bán hàng (POS)
 * form's "Tìm khách hàng (F4)" search + "+ Thêm khách hàng" quick-add modal.
 * No dedicated list/filters page yet (that's the still-unbuilt "Khách hàng"
 * dashboard tab) - same scope-cut reasoning as SupplierController.
 */
@RestController
@RequestMapping("/store/customers")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@Slf4j
public class CustomerController {

    private static final String CODE_PREFIX = "KH";
    private static final int MAX_CODE_RETRIES = 5;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TenantGuard tenantGuard;

    @Autowired
    private SubscriptionGuard subscriptionGuard;

    /** GET /api/store/customers?query=... - used by the POS "Tìm khách hàng (F4)" search box. */
    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String query) {
        try {
            List<Customer> customers = (query != null && !query.trim().isBlank())
                    ? customerRepository.search(query.trim(), tenantGuard.requireStore())
                    : customerRepository.findByActiveTrueOrderByNameAsc();
            return ResponseEntity.ok(Map.of("customers", customers));
        } catch (Exception e) {
            log.error("Failed to list customers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve customers"));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CustomerRequest request) {
        try {
            Long storeId = tenantGuard.requireStore();
            subscriptionGuard.requireActiveSubscription(storeId);

            Customer customer = Customer.builder()
                    .store(tenantGuard.currentStoreRef())
                    .name(request.name().trim())
                    .phone(request.phone())
                    .email(request.email())
                    .dateOfBirth(request.dateOfBirth())
                    .gender(request.gender())
                    .address(request.address())
                    .region(request.region())
                    .ward(request.ward())
                    .groupName(request.groupName())
                    .note(request.note())
                    .active(true)
                    .build();

            // Retry on the rare race where two requests generate the same
            // next-in-sequence code concurrently (same pattern as SupplierController).
            DataIntegrityViolationException lastError = null;
            for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
                customer.setCode(SequentialCodeGenerator.generate(CODE_PREFIX, customerRepository.countByStoreId(storeId) + attempt));
                try {
                    Customer saved = customerRepository.save(customer);
                    log.info("New customer created: {} (code {})", saved.getName(), saved.getCode());
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(Map.of("message", "Customer created successfully", "customer", saved));
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
            log.error("Failed to create customer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create customer"));
        }
    }
}

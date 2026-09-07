package com.ut.edu.backend.policy;

import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * "Chính sách cửa hàng" - minimal CRUD backing the Dashboard's Chính sách
 * page and read by the storefront AI chat (ChatToolExecutor). Same shape as
 * SupplierController.
 */
@RestController
@RequestMapping("/store/policies")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@Slf4j
public class StorePolicyController {

    @Autowired
    private StorePolicyRepository storePolicyRepository;

    @Autowired
    private TenantGuard tenantGuard;

    @Autowired
    private SubscriptionGuard subscriptionGuard;

    private StorePolicy findStorePolicy(Long id) {
        return storePolicyRepository.findById(id)
                .filter(p -> tenantGuard.isCurrentStore(p.getStore()))
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + id));
    }

    @GetMapping
    public ResponseEntity<?> list() {
        try {
            List<StorePolicy> policies = storePolicyRepository.findByActiveTrueOrderByDisplayOrderAsc();
            return ResponseEntity.ok(Map.of("policies", policies));
        } catch (Exception e) {
            log.error("Failed to list store policies", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve policies"));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody StorePolicyRequest request) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());

            StorePolicy policy = StorePolicy.builder()
                    .store(tenantGuard.currentStoreRef())
                    .title(request.title().trim())
                    .content(request.content().trim())
                    .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                    .active(true)
                    .build();

            StorePolicy saved = storePolicyRepository.save(policy);
            log.info("New store policy created: {}", saved.getTitle());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Policy created successfully", "policy", saved));
        } catch (jakarta.validation.ConstraintViolationException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (com.ut.edu.backend.exception.SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create store policy", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create policy"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody StorePolicyRequest request) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            StorePolicy policy = findStorePolicy(id);
            policy.setTitle(request.title().trim());
            policy.setContent(request.content().trim());
            if (request.displayOrder() != null) {
                policy.setDisplayOrder(request.displayOrder());
            }
            StorePolicy saved = storePolicyRepository.save(policy);
            return ResponseEntity.ok(Map.of("message", "Policy updated successfully", "policy", saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (com.ut.edu.backend.exception.SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update store policy: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update policy"));
        }
    }

    /** Soft delete (active=false), same convention as Supplier/Category. (Product deletes for real - see AdminProductController#deleteProduct.) */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            subscriptionGuard.requireActiveSubscription(tenantGuard.requireStore());
            StorePolicy policy = findStorePolicy(id);
            policy.setActive(false);
            storePolicyRepository.save(policy);
            return ResponseEntity.ok(Map.of("message", "Policy deleted successfully", "policyId", id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (com.ut.edu.backend.exception.SubscriptionRequiredException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete store policy: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete policy"));
        }
    }
}

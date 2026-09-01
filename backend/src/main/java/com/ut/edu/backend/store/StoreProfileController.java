package com.ut.edu.backend.store;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Current store's own profile - lets a logged-in owner/manager resolve
 * their own slug (e.g. to link to their public storefront) without knowing
 * it up front. Mirrors StorefrontController's public response shape, but
 * resolves the store from the JWT's tenant (TenantContext) instead of a URL
 * slug. OWNER/MANAGER only, matching SecurityConfig's blanket /store/**
 * rule (STAFF is blocked at the URL layer before reaching any controller
 * under this prefix).
 */
@RestController
@RequestMapping("/store")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
@RequiredArgsConstructor
public class StoreProfileController {

    private final StoreRepository storeRepository;
    private final TenantGuard tenantGuard;

    @GetMapping
    public ResponseEntity<?> currentStore() {
        Store store = storeRepository.findById(tenantGuard.requireStore())
                .orElseThrow(() -> new IllegalStateException("Store not found for current tenant"));

        return ResponseEntity.ok(Map.of(
                "id", store.getId(),
                "name", store.getName(),
                "slug", store.getSlug(),
                "logoUrl", store.getLogoUrl() != null ? store.getLogoUrl() : "",
                "phone", store.getPhone() != null ? store.getPhone() : "",
                "address", store.getAddress() != null ? store.getAddress() : ""
        ));
    }
}

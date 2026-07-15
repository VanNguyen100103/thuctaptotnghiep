package com.ut.edu.backend.store;

import com.ut.edu.backend.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Service-layer double-check for tenant isolation (defense in depth).
 *
 * The Hibernate tenant filter already scopes queries, but it does not cover
 * {@code em.find()} by primary key or entities reached through associations.
 * Every WRITE operation on a tenant-owned resource must call
 * {@link #requireSameStore(Store)} before mutating it, so a user of store A
 * can never modify data of store B via a guessed id (cross-tenant IDOR).
 */
@Component
@RequiredArgsConstructor
public class TenantGuard {

    private final StoreRepository storeRepository;

    /**
     * True when the given store is the one bound to the current request.
     * Use to treat cross-tenant reads by id as "not found".
     */
    public boolean isCurrentStore(Store resourceStore) {
        Long storeId = TenantContext.getStoreId();
        return storeId != null && resourceStore != null && storeId.equals(resourceStore.getId());
    }

    /**
     * Lightweight reference to the current store, for setting the tenant
     * on newly created entities (no SELECT issued).
     */
    public Store currentStoreRef() {
        return storeRepository.getReferenceById(requireStore());
    }

    /**
     * Ensures a tenant is resolved for the current request
     * (owner/staff JWT or storefront slug), and returns its id.
     */
    public Long requireStore() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new AccessDeniedException("No store bound to the current request");
        }
        return storeId;
    }

    /**
     * Verifies the resource belongs to the current tenant. Throws 404 (not 403)
     * so the existence of another store's resource is not revealed.
     */
    public void requireSameStore(Store resourceStore) {
        Long storeId = requireStore();
        if (resourceStore == null || !storeId.equals(resourceStore.getId())) {
            throw new ResourceNotFoundException("Resource not found in current store");
        }
    }
}

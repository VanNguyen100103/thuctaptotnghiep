package com.ut.edu.backend.store;

/**
 * Holds the storeId (tenant) of the current request in a ThreadLocal.
 *
 * Populated by {@link TenantResolverFilter} from either the JWT (staff/owner)
 * or the storefront slug in the URL, and cleared after every request.
 * When empty (platform admin, background jobs, legacy routes) the Hibernate
 * tenant filter stays disabled and queries see all stores.
 */
public final class TenantContext {

    /** Name of the Hibernate filter defined on {@code BaseEntity}. */
    public static final String TENANT_FILTER = "tenantFilter";

    /** Name of the filter parameter carrying the current store id. */
    public static final String PARAM_STORE_ID = "storeId";

    private static final ThreadLocal<Long> CURRENT_STORE = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setStoreId(Long storeId) {
        CURRENT_STORE.set(storeId);
    }

    public static Long getStoreId() {
        return CURRENT_STORE.get();
    }

    public static boolean hasStore() {
        return CURRENT_STORE.get() != null;
    }

    public static void clear() {
        CURRENT_STORE.remove();
    }
}

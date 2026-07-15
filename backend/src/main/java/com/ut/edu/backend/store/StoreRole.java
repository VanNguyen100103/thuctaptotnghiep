package com.ut.edu.backend.store;

/**
 * Role of a user within a store (tenant).
 * SUPER_ADMIN is platform-wide (SaaS operator), not tied to any store.
 */
public enum StoreRole {
    OWNER,
    MANAGER,
    STAFF,
    SUPER_ADMIN
}

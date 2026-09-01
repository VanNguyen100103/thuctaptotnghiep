package com.ut.edu.backend.store;

/**
 * SaaS pricing plans.
 * FREE_TRIAL: 14 days, full features.
 * BASIC: limited (e.g. 50 products, 1 staff member).
 * PRO: unlimited + AI recommendations + Elasticsearch.
 */
public enum SubscriptionPlan {
    FREE_TRIAL(-1, -1),
    BASIC(50, 1),
    PRO(-1, -1);

    /** -1 means unlimited. */
    private final int maxProducts;
    private final int maxStaff;

    SubscriptionPlan(int maxProducts, int maxStaff) {
        this.maxProducts = maxProducts;
        this.maxStaff = maxStaff;
    }

    public int getMaxProducts() {
        return maxProducts;
    }

    public int getMaxStaff() {
        return maxStaff;
    }
}

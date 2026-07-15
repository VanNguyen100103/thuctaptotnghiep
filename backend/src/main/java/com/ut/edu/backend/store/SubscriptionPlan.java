package com.ut.edu.backend.store;

/**
 * SaaS pricing plans.
 * FREE_TRIAL: 14 days, full features.
 * BASIC: limited (e.g. 50 products, 1 staff member).
 * PRO: unlimited + AI recommendations + Elasticsearch.
 */
public enum SubscriptionPlan {
    FREE_TRIAL,
    BASIC,
    PRO
}

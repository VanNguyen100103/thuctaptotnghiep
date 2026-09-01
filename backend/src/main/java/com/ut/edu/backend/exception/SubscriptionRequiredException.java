package com.ut.edu.backend.exception;

/**
 * Thrown when a store's subscription is missing/expired, or a plan-tier
 * limit (products, staff) has been reached.
 */
public class SubscriptionRequiredException extends RuntimeException {

    public SubscriptionRequiredException(String message) {
        super(message);
    }
}

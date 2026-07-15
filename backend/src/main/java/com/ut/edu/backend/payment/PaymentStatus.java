package com.ut.edu.backend.payment;

/**
 * Payment status enum for tracking payment lifecycle
 */
public enum PaymentStatus {
    PENDING,         // Payment initiated
    PROCESSING,      // Payment being processed by PayPal
    COMPLETED,       // Payment successful
    FAILED,          // Payment failed
    CANCELLED,       // Payment cancelled
    REFUNDED,        // Payment refunded
    PARTIALLY_REFUNDED  // Partial refund processed
}

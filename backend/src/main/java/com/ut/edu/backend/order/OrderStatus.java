package com.ut.edu.backend.order;

import com.ut.edu.backend.payment.Payment;

/**
 * Order status enum for tracking order lifecycle
 */
public enum OrderStatus {
    PENDING,           // Order created, awaiting payment
    PAYMENT_PENDING,   // Payment initiated
    PAID,              // Payment confirmed
    PROCESSING,        // Order being prepared
    SHIPPED,           // Order shipped
    DELIVERED,         // Order delivered
    CANCELLED,         // Order cancelled by user or admin
    REFUNDED,          // Payment refunded
    FAILED             // Payment or processing failed
}

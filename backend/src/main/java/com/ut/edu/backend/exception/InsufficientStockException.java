package com.ut.edu.backend.exception;

/**
 * Exception thrown when product stock is insufficient for requested quantity
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }

    public InsufficientStockException(String productName, Integer availableStock, Integer requestedQuantity) {
        super(String.format("Insufficient stock for product '%s'. Available: %d, Requested: %d",
                productName, availableStock, requestedQuantity));
    }
}

package com.ut.edu.backend.sale;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * One cart line at checkout. unitPrice is optional - if omitted the
 * product's current sale price is used; if given (a cashier line-price
 * override) it is trusted, same as PurchaseOrderItemRequest's unitPrice,
 * and safe here for the same reason: this endpoint is already
 * OWNER/MANAGER-only.
 */
public record SaleItemRequest(
        @NotNull(message = "productId is required") Long productId,
        @NotNull(message = "quantity is required") @Min(value = 1, message = "quantity must be at least 1") Integer quantity,
        @DecimalMin(value = "0.0", message = "unitPrice cannot be negative") BigDecimal unitPrice,
        @DecimalMin(value = "0.0", message = "discountAmount cannot be negative") BigDecimal discountAmount) {
}

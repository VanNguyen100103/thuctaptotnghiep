package com.ut.edu.backend.purchaseorder;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PurchaseOrderItemRequest(
        @NotNull(message = "productId is required") Long productId,
        @NotNull(message = "quantity is required") @Min(value = 1, message = "quantity must be at least 1") Integer quantity,
        @NotNull(message = "unitPrice is required") @DecimalMin(value = "0.0", message = "unitPrice cannot be negative") BigDecimal unitPrice,
        @DecimalMin(value = "0.0", message = "discountAmount cannot be negative") BigDecimal discountAmount) {
}

package com.ut.edu.backend.purchaseorder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body for both creating a new draft (POST) and saving an existing one
 * (PUT, DRAFT only) - "Lưu tạm" in the UI. Items may be empty (an
 * in-progress draft with just header info); PurchaseOrderService#complete
 * is the one that requires at least one line.
 */
public record SavePurchaseOrderRequest(
        Long supplierId,
        @DecimalMin(value = "0.0", message = "discountAmount cannot be negative") BigDecimal discountAmount,
        @DecimalMin(value = "0.0", message = "amountPaid cannot be negative") BigDecimal amountPaid,
        @DecimalMin(value = "0.0", message = "otherCosts cannot be negative") BigDecimal otherCosts,
        String note,
        @Valid List<PurchaseOrderItemRequest> items) {
}

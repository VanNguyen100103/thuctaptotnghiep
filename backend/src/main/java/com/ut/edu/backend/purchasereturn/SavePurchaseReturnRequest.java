package com.ut.edu.backend.purchasereturn;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body for both creating a new draft (POST) and saving an existing one
 * (PUT, DRAFT only) - "Lưu tạm" in the UI. Items may be empty (a draft with
 * just header info); PurchaseReturnService#complete is the one that requires
 * at least one line.
 */
public record SavePurchaseReturnRequest(
        Long supplierId,
        @DecimalMin(value = "0.0", message = "discountAmount cannot be negative") BigDecimal discountAmount,
        @DecimalMin(value = "0.0", message = "amountReceived cannot be negative") BigDecimal amountReceived,
        String note,
        @Valid List<PurchaseReturnItemRequest> items) {
}

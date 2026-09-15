package com.ut.edu.backend.purchasereturn;

import java.math.BigDecimal;

public record PurchaseReturnItemResponse(
        Long id,
        Long productId,
        String productName,
        String productSku,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal lineTotal) {

    static PurchaseReturnItemResponse from(PurchaseReturnItem item) {
        return new PurchaseReturnItemResponse(
                item.getId(),
                item.getProduct() != null ? item.getProduct().getId() : null,
                item.getProductName(),
                item.getProductSku(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountAmount(),
                item.getLineTotal());
    }
}

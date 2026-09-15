package com.ut.edu.backend.salereturn;

import java.math.BigDecimal;

public record SaleReturnItemResponse(
        Long id,
        Long saleItemId,
        Long productId,
        String productName,
        String productSku,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal lineTotal) {

    static SaleReturnItemResponse from(SaleReturnItem item) {
        return new SaleReturnItemResponse(
                item.getId(),
                item.getSaleItem() != null ? item.getSaleItem().getId() : null,
                item.getProduct() != null ? item.getProduct().getId() : null,
                item.getProductName(),
                item.getProductSku(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountAmount(),
                item.getLineTotal());
    }
}

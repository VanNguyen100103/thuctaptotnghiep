package com.ut.edu.backend.order;

import java.math.BigDecimal;

/**
 * One line of a customer order, as the dashboard's "Đặt hàng" detail panel
 * shows it. Reads the snapshot columns on OrderItem rather than the product
 * it points at, so a line still renders after its product is deleted.
 */
public record StoreOrderItemResponse(
        Long id,
        Long productId,
        String productName,
        String productSku,
        /** The variant the customer picked, already joined for display ("Xanh - M"); null when the line has no variant. */
        String variantLabel,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal lineTotal) {

    static StoreOrderItemResponse from(OrderItem item) {
        return new StoreOrderItemResponse(
                item.getId(),
                // Reading the id off the lazy proxy does not load the product.
                item.getProduct() != null ? item.getProduct().getId() : null,
                item.getProductName(),
                item.getProductSku(),
                variantLabel(item),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountAmount(),
                item.getSubtotal());
    }

    private static String variantLabel(OrderItem item) {
        StringBuilder label = new StringBuilder();
        if (item.getSelectedColor() != null && !item.getSelectedColor().isBlank()) {
            label.append(item.getSelectedColor().trim());
        }
        if (item.getSelectedSize() != null && !item.getSelectedSize().isBlank()) {
            if (label.length() > 0) {
                label.append(" - ");
            }
            label.append(item.getSelectedSize().trim());
        }
        return label.length() == 0 ? null : label.toString();
    }
}

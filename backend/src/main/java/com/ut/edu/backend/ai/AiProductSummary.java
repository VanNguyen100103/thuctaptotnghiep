package com.ut.edu.backend.ai;

import com.ut.edu.backend.category.Category;
import com.ut.edu.backend.product.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Read-only product view handed to the AI as a tool result. Built by
 * explicit field selection (not by nulling fields on a fetched Product) so
 * store-internal data (costPrice, taxRate, notes) can never leak here even
 * if new fields are added to Product later - same intent as
 * StorefrontController#hideInternalFields, just enforced by construction.
 */
public record AiProductSummary(
        Long id, String name, String shortDescription, BigDecimal price, BigDecimal compareAtPrice,
        boolean inStock, String brand, String material, Map<String, String> attributes, List<String> categoryNames) {

    private static final int MAX_DESCRIPTION_LENGTH = 300;

    public static AiProductSummary from(Product p) {
        return new AiProductSummary(
                p.getId(),
                p.getName(),
                truncate(p.getShortDescription()),
                p.getPrice(),
                p.getCompareAtPrice(),
                p.getStockQuantity() != null && p.getStockQuantity() > 0,
                p.getBrand(),
                p.getMaterial(),
                p.getAttributes(),
                p.getCategories().stream().map(Category::getName).toList()
        );
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_DESCRIPTION_LENGTH ? s : s.substring(0, MAX_DESCRIPTION_LENGTH) + "...";
    }
}

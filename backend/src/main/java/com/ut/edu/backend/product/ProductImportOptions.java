package com.ut.edu.backend.product;

/**
 * Mirrors the 5 KiotViet-style radio choices on the import dialog. Every flag
 * defaults to false (the dialog's own default is always the first/"Không"/
 * "Báo lỗi" option).
 */
public record ProductImportOptions(
        boolean replaceDuplicateName,
        boolean replaceDuplicateSku,
        boolean updateStock,
        boolean updateCostPrice,
        boolean updateDescription
) {
}

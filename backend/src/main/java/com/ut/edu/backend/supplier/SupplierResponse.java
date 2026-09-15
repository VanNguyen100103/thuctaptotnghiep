package com.ut.edu.backend.supplier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the "Nhà cung cấp" list. The entity's own fields plus the two
 * money columns KiotViet's list carries - "Tổng mua" and "Nợ cần trả hiện
 * tại" - plus "Tổng trả hàng" beside them. None of the three live on the
 * supplier: they are rolled up per request from the store's goods receipts
 * and return notes (see PurchaseOrderRepository#sumPurchasedBySupplier and
 * PurchaseReturnRepository#sumReturnedBySupplier).
 */
public record SupplierResponse(
        Long id,
        String code,
        String name,
        String phone,
        String email,
        String address,
        String region,
        String ward,
        String groupName,
        String taxCode,
        String companyName,
        String note,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        BigDecimal totalPurchase,
        /** "Tổng trả hàng" - completed Trả hàng nhập documents in the same range. Kept apart from totalPurchase, which stays the gross figure KiotViet shows. */
        BigDecimal totalReturn,
        BigDecimal currentDebt) {

    public static SupplierResponse of(Supplier supplier, BigDecimal totalPurchase, BigDecimal totalReturn, BigDecimal currentDebt) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getCode(),
                supplier.getName(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.getRegion(),
                supplier.getWard(),
                supplier.getGroupName(),
                supplier.getTaxCode(),
                supplier.getCompanyName(),
                supplier.getNote(),
                supplier.getActive(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt(),
                totalPurchase == null ? BigDecimal.ZERO : totalPurchase,
                totalReturn == null ? BigDecimal.ZERO : totalReturn,
                currentDebt == null ? BigDecimal.ZERO : currentDebt);
    }

    /** A supplier on its own, before any receipt has been rolled up against it - what create/update hand back. */
    public static SupplierResponse of(Supplier supplier) {
        return of(supplier, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}

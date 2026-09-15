package com.ut.edu.backend.purchasereturn;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long>, JpaSpecificationExecutor<PurchaseReturn> {

    long countByStoreId(Long storeId);

    long countBySupplierId(Long supplierId);

    /** Distinct "Người tạo" on this store's return notes - the sidebar picker's options. */
    @Query("SELECT DISTINCT pr.createdBy.username FROM PurchaseReturn pr "
            + "WHERE pr.store.id = :storeId AND pr.createdBy IS NOT NULL "
            + "ORDER BY pr.createdBy.username")
    List<String> findCreatorUsernames(@Param("storeId") Long storeId);

    /** Distinct "Người trả" - only set once a return is completed, so this is the shorter list. */
    @Query("SELECT DISTINCT pr.completedBy.username FROM PurchaseReturn pr "
            + "WHERE pr.store.id = :storeId AND pr.completedBy IS NOT NULL "
            + "ORDER BY pr.completedBy.username")
    List<String> findReceiverUsernames(@Param("storeId") Long storeId);

    /**
     * "Tổng trả hàng" per supplier, inside the Nhà cung cấp sidebar's Thời
     * gian range - the returns half of what
     * PurchaseOrderRepository#sumPurchasedBySupplier does for receipts, and
     * bounded the same way (two real bounds, no nullable ends).
     */
    @Query("SELECT pr.supplier.id AS supplierId, SUM(pr.refundAmount) AS amount FROM PurchaseReturn pr "
            + "WHERE pr.store.id = :storeId AND pr.status = :status AND pr.supplier IS NOT NULL "
            + "AND COALESCE(pr.completedAt, pr.createdAt) BETWEEN :from AND :to "
            + "GROUP BY pr.supplier.id")
    List<SupplierAmount> sumReturnedBySupplier(@Param("storeId") Long storeId,
                                               @Param("status") PurchaseReturnStatus status,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    /**
     * What each supplier's completed returns take off the debt owed to them:
     * the refund they owe back, less anything already handed over in cash.
     * All-time, for the same reason the receipts' debt query is.
     */
    @Query("SELECT pr.supplier.id AS supplierId, SUM(pr.refundAmount - pr.amountReceived) AS amount FROM PurchaseReturn pr "
            + "WHERE pr.store.id = :storeId AND pr.status = :status AND pr.supplier IS NOT NULL "
            + "GROUP BY pr.supplier.id")
    List<SupplierAmount> sumDebtCreditBySupplier(@Param("storeId") Long storeId,
                                                 @Param("status") PurchaseReturnStatus status);

    /** Same projection shape as PurchaseOrderRepository.SupplierAmount, so SupplierController can fold both sides together. */
    interface SupplierAmount {
        Long getSupplierId();

        BigDecimal getAmount();
    }

    /**
     * Cuts these products loose from the return lines they appear on, so the
     * products can be deleted while the return history keeps its snapshot -
     * the counterpart of PurchaseOrderRepository#detachProducts, called from
     * the same place.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PurchaseReturnItem pri SET pri.product = null WHERE pri.product.id IN :productIds")
    void detachProducts(@Param("productIds") List<Long> productIds);
}

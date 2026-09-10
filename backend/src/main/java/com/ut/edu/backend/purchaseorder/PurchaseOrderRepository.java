package com.ut.edu.backend.purchaseorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {

    long countByStoreId(Long storeId);

    /**
     * Distinct "Người tạo" on this store's goods receipts - the options the
     * Nhập hàng sidebar offers. The store id is passed rather than left to
     * the tenant filter, so the list is right even when this runs outside a
     * filtered session (same reasoning as SaleRepository#findSellerUsernames).
     */
    @Query("SELECT DISTINCT po.createdBy.username FROM PurchaseOrder po "
            + "WHERE po.store.id = :storeId AND po.createdBy IS NOT NULL "
            + "ORDER BY po.createdBy.username")
    List<String> findCreatorUsernames(@Param("storeId") Long storeId);

    /** Distinct "Người nhập" - only set once a receipt is completed, so this is the shorter list. */
    @Query("SELECT DISTINCT po.completedBy.username FROM PurchaseOrder po "
            + "WHERE po.store.id = :storeId AND po.completedBy IS NOT NULL "
            + "ORDER BY po.completedBy.username")
    List<String> findReceiverUsernames(@Param("storeId") Long storeId);

    /** How many receipts point at this supplier - what stands between "Xóa" and "Ngừng hoạt động" on the Nhà cung cấp screen. */
    long countBySupplierId(Long supplierId);

    /**
     * "Tổng mua" per supplier: what the store has bought from each of them,
     * inside the Nhà cung cấp sidebar's Thời gian range. Only completed
     * receipts count - a Phiếu tạm is a document nobody has received goods
     * against yet, and a cancelled one never happened.
     *
     * The range is passed as two real bounds rather than nullable ones:
     * "toàn thời gian" sends the widest pair the column can hold, which keeps
     * this a single query instead of one per combination of open ends.
     */
    @Query("SELECT po.supplier.id AS supplierId, SUM(po.payableAmount) AS amount FROM PurchaseOrder po "
            + "WHERE po.store.id = :storeId AND po.status = :status AND po.supplier IS NOT NULL "
            + "AND COALESCE(po.completedAt, po.createdAt) BETWEEN :from AND :to "
            + "GROUP BY po.supplier.id")
    List<SupplierAmount> sumPurchasedBySupplier(@Param("storeId") Long storeId,
                                                @Param("status") PurchaseOrderStatus status,
                                                @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);

    /**
     * "Nợ cần trả hiện tại" per supplier - what is still owed on completed
     * receipts (nghĩa vụ − đã trả). Deliberately not date-filtered like
     * {@link #sumPurchasedBySupplier}: a debt is what stands today, not what
     * was run up inside the range the sidebar happens to be showing.
     */
    @Query("SELECT po.supplier.id AS supplierId, SUM(po.payableAmount - po.amountPaid) AS amount FROM PurchaseOrder po "
            + "WHERE po.store.id = :storeId AND po.status = :status AND po.supplier IS NOT NULL "
            + "GROUP BY po.supplier.id")
    List<SupplierAmount> sumDebtBySupplier(@Param("storeId") Long storeId,
                                           @Param("status") PurchaseOrderStatus status);

    /** One supplier's rolled-up money column, as returned by the two GROUP BY queries above. */
    interface SupplierAmount {
        Long getSupplierId();

        BigDecimal getAmount();
    }

    /**
     * Cuts these products loose from the purchase-order ("Nhập hàng") lines
     * they appear on, so the products can be deleted while the stock-in
     * history keeps its snapshot.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PurchaseOrderItem poi SET poi.product = null WHERE poi.product.id IN :productIds")
    void detachProducts(@Param("productIds") List<Long> productIds);
}

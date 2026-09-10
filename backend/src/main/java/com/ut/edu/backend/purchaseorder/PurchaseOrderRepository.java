package com.ut.edu.backend.purchaseorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    /**
     * Cuts these products loose from the purchase-order ("Nhập hàng") lines
     * they appear on, so the products can be deleted while the stock-in
     * history keeps its snapshot.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PurchaseOrderItem poi SET poi.product = null WHERE poi.product.id IN :productIds")
    void detachProducts(@Param("productIds") List<Long> productIds);
}

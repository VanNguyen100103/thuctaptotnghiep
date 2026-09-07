package com.ut.edu.backend.purchaseorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {

    long countByStoreId(Long storeId);

    /**
     * Which of these products already appear on a purchase order ("Nhập
     * hàng") - such a product cannot be hard-deleted without tearing a line
     * item out of the stock-in history. One query for the whole batch.
     */
    @Query("SELECT DISTINCT poi.product.id FROM PurchaseOrderItem poi WHERE poi.product.id IN :productIds")
    List<Long> findProductIdsOnPurchaseOrders(@Param("productIds") List<Long> productIds);
}

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
     * Cuts these products loose from the purchase-order ("Nhập hàng") lines
     * they appear on, so the products can be deleted while the stock-in
     * history keeps its snapshot.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PurchaseOrderItem poi SET poi.product = null WHERE poi.product.id IN :productIds")
    void detachProducts(@Param("productIds") List<Long> productIds);
}

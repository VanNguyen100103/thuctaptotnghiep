package com.ut.edu.backend.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    long countByStoreId(Long storeId);

    /**
     * Distinct "Người bán" on this store's invoices - the options the Hóa đơn
     * filter offers. The store id is passed rather than left to the tenant
     * filter, so the list is right even when this runs outside a filtered
     * session.
     */
    @Query("SELECT DISTINCT s.createdBy.username FROM Sale s "
            + "WHERE s.store.id = :storeId AND s.createdBy IS NOT NULL "
            + "ORDER BY s.createdBy.username")
    List<String> findSellerUsernames(@Param("storeId") Long storeId);

    /**
     * Cuts these products loose from the POS sale lines they appear on, so
     * the products can be deleted while the sales keep their snapshot.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SaleItem si SET si.product = null WHERE si.product.id IN :productIds")
    void detachProducts(@Param("productIds") List<Long> productIds);
}

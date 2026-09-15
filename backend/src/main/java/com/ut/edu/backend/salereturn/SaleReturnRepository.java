package com.ut.edu.backend.salereturn;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long>, JpaSpecificationExecutor<SaleReturn> {

    long countByStoreId(Long storeId);

    /** Distinct "Người trả hàng" on this store's return notes - the sidebar picker's options. */
    @Query("SELECT DISTINCT sr.createdBy.username FROM SaleReturn sr "
            + "WHERE sr.store.id = :storeId AND sr.createdBy IS NOT NULL "
            + "ORDER BY sr.createdBy.username")
    List<String> findCreatorUsernames(@Param("storeId") Long storeId);

    /**
     * How many units of each invoice line this invoice has already had
     * returned, across every earlier return document. What caps the next one -
     * see SaleReturnService#create.
     */
    @Query("SELECT sri.saleItem.id AS saleItemId, SUM(sri.quantity) AS quantity FROM SaleReturnItem sri "
            + "WHERE sri.saleReturn.sale.id = :saleId GROUP BY sri.saleItem.id")
    List<ReturnedQuantity> sumReturnedQuantitiesBySale(@Param("saleId") Long saleId);

    interface ReturnedQuantity {
        Long getSaleItemId();

        Long getQuantity();
    }

    /**
     * The loyalty points this invoice's earlier returns have already put back
     * and taken back. Caps the next return's own two figures, so rounding on
     * a line-by-line return can never add up to more than the sale itself
     * redeemed or earned.
     */
    @Query("SELECT COALESCE(SUM(sr.pointsRestored), 0) FROM SaleReturn sr WHERE sr.sale.id = :saleId")
    int sumPointsRestoredBySale(@Param("saleId") Long saleId);

    @Query("SELECT COALESCE(SUM(sr.pointsReverted), 0) FROM SaleReturn sr WHERE sr.sale.id = :saleId")
    int sumPointsRevertedBySale(@Param("saleId") Long saleId);

    /**
     * Cuts these products loose from the return lines they appear on, so the
     * products can be deleted while the return history keeps its snapshot -
     * the counterpart of SaleRepository#detachProducts, called from the same
     * place.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SaleReturnItem sri SET sri.product = null WHERE sri.product.id IN :productIds")
    void detachProducts(@Param("productIds") List<Long> productIds);
}

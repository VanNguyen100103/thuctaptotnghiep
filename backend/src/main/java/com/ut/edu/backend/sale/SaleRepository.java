package com.ut.edu.backend.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    long countByStoreId(Long storeId);

    /**
     * Which of these products already appear on a POS sale - such a product
     * cannot be hard-deleted without tearing a line item out of the sales
     * history. One query for the whole batch.
     */
    @Query("SELECT DISTINCT si.product.id FROM SaleItem si WHERE si.product.id IN :productIds")
    List<Long> findProductIdsOnSales(@Param("productIds") List<Long> productIds);
}

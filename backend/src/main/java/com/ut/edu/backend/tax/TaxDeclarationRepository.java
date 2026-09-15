package com.ut.edu.backend.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxDeclarationRepository extends JpaRepository<TaxDeclaration, Long> {

    /** Every stored decision for one year, loaded in one go and matched against the calendar's periods in memory. */
    List<TaxDeclaration> findByStoreIdAndPeriodYearAndPeriodType(Long storeId, Integer periodYear, TaxPeriodType periodType);

    Optional<TaxDeclaration> findByStoreIdAndPeriodYearAndPeriodTypeAndPeriodNumber(
            Long storeId, Integer periodYear, TaxPeriodType periodType, Integer periodNumber);

    /** Years the shop has already touched - offered in the year picker alongside the current one. */
    List<TaxDeclaration> findByStoreIdOrderByPeriodYearDesc(Long storeId);
}

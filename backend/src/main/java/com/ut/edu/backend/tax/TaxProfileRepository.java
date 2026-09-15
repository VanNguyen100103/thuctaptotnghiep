package com.ut.edu.backend.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaxProfileRepository extends JpaRepository<TaxProfile, Long> {

    /**
     * The store id is passed rather than left to the Hibernate tenant filter:
     * this runs on the read path of every tax screen, and a missing filter
     * would hand back another store's taxpayer identity rather than an empty
     * result.
     */
    Optional<TaxProfile> findByStoreId(Long storeId);
}

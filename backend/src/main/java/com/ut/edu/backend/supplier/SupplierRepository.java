package com.ut.edu.backend.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    long countByStoreId(Long storeId);

    List<Supplier> findByActiveTrueOrderByNameAsc();

    /**
     * "Tìm nhà cung cấp" - matches on code/name/phone, Vietnamese-diacritic
     * insensitive on the name. Native (not JPQL) for the same reason as
     * ProductRepository's own unaccent-based search: Hibernate 6's HQL type
     * checker rejects `unaccent(...)` as an untyped LIKE operand, so the
     * native form is needed - and that means it bypasses the Hibernate
     * tenant filter, so storeId is passed explicitly.
     */
    @Query(value = "SELECT * FROM suppliers s WHERE s.active = true AND s.store_id = :storeId AND ("
            + "LOWER(s.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(s.phone) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "unaccent(LOWER(s.name)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%')) "
            + "ORDER BY s.name ASC",
            nativeQuery = true)
    List<Supplier> search(@Param("keyword") String keyword, @Param("storeId") Long storeId);
}

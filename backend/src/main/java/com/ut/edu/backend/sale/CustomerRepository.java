package com.ut.edu.backend.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    long countByStoreId(Long storeId);

    List<Customer> findByActiveTrueOrderByNameAsc();

    /**
     * "Tìm khách hàng (F4)" - matches on code/phone/name, Vietnamese-diacritic
     * insensitive on the name. Native (not JPQL) for the same reason as
     * SupplierRepository#search: Hibernate 6's HQL type checker rejects
     * unaccent(...) as an untyped LIKE operand - native bypasses the
     * Hibernate tenant filter, so storeId is passed explicitly.
     */
    @Query(value = "SELECT * FROM customers c WHERE c.active = true AND c.store_id = :storeId AND ("
            + "LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(c.phone) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "unaccent(LOWER(c.name)) LIKE CONCAT('%', unaccent(LOWER(:keyword)), '%')) "
            + "ORDER BY c.name ASC",
            nativeQuery = true)
    List<Customer> search(@Param("keyword") String keyword, @Param("storeId") Long storeId);
}

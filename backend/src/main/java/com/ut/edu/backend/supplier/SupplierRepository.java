package com.ut.edu.backend.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    long countByStoreId(Long storeId);

    /**
     * Every supplier of the current store, active or not - the Nhà cung cấp
     * screen's own list, whose "Trạng thái" filter offers "Ngừng hoạt động"
     * and so cannot start from an active-only query. Also what the Nhập hàng
     * form's supplier box searches, since SupplierController#list matches the
     * keyword in memory (unaccented, like the native query this replaced).
     */
    List<Supplier> findAllByOrderByNameAsc();

    /**
     * The "Nhóm nhà cung cấp" dropdown's options. groupName is free text on
     * the supplier (no group entity behind it - same treatment as
     * Product#brand), so the options are whatever the shop has actually
     * typed, which is also why an empty group can never be offered.
     */
    @Query("SELECT DISTINCT s.groupName FROM Supplier s "
            + "WHERE s.store.id = :storeId AND s.groupName IS NOT NULL AND s.groupName <> '' "
            + "ORDER BY s.groupName")
    List<String> findGroupNames(@Param("storeId") Long storeId);
}

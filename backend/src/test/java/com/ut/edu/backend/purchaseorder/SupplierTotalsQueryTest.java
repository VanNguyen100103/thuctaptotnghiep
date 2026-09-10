package com.ut.edu.backend.purchaseorder;

import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.StoreStatus;
import com.ut.edu.backend.supplier.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two money columns on the Nhà cung cấp list, against a real PostgreSQL:
 * these are GROUP BY queries whose JPQL only becomes SQL at runtime, and
 * whose date bound goes through COALESCE(completedAt, createdAt) - none of
 * which a mocked repository can prove. Requires a running Docker daemon,
 * like BackendApplicationTests.
 */
@DataJpaTest
@ActiveProfiles("test")
@Testcontainers
class SupplierTotalsQueryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    private Long storeId;
    private Supplier nuocSach;
    private Supplier bepGas;

    @BeforeEach
    void setUp() {
        Store store = persistStore("Cửa hàng A", "cua-hang-a");
        Store otherStore = persistStore("Cửa hàng B", "cua-hang-b");
        storeId = store.getId();

        nuocSach = persistSupplier(store, "NCC000001", "Nước sạch");
        bepGas = persistSupplier(store, "NCC000002", "Bếp gas");
        Supplier otherStoreSupplier = persistSupplier(otherStore, "NCC000001", "Nhà cung cấp của shop khác");

        // Nước sạch: two completed deliveries in different months, one still a draft.
        persistReceipt(store, nuocSach, "PN000001", PurchaseOrderStatus.COMPLETED,
                "2400000", "1000000", LocalDateTime.of(2026, 9, 5, 10, 0));
        persistReceipt(store, nuocSach, "PN000002", PurchaseOrderStatus.COMPLETED,
                "600000", "600000", LocalDateTime.of(2026, 8, 5, 10, 0));
        persistReceipt(store, nuocSach, "PN000003", PurchaseOrderStatus.DRAFT,
                "999999", "0", null);

        persistReceipt(store, bepGas, "PN000004", PurchaseOrderStatus.COMPLETED,
                "500000", "0", LocalDateTime.of(2026, 9, 20, 10, 0));

        // Another store's receipt, which neither query may ever count.
        persistReceipt(otherStore, otherStoreSupplier, "PN000001", PurchaseOrderStatus.COMPLETED,
                "700000", "0", LocalDateTime.of(2026, 9, 10, 10, 0));

        entityManager.flush();
    }

    private Store persistStore(String name, String slug) {
        Store store = new Store();
        store.setName(name);
        store.setSlug(slug);
        store.setStatus(StoreStatus.TRIAL);
        return entityManager.persist(store);
    }

    private Supplier persistSupplier(Store store, String code, String name) {
        return entityManager.persist(Supplier.builder()
                .store(store)
                .code(code)
                .name(name)
                .active(true)
                .build());
    }

    private void persistReceipt(Store store, Supplier supplier, String code, PurchaseOrderStatus status,
                                String payable, String paid, LocalDateTime completedAt) {
        entityManager.persist(PurchaseOrder.builder()
                .store(store)
                .supplier(supplier)
                .code(code)
                .status(status)
                .totalGoodsValue(new BigDecimal(payable))
                .payableAmount(new BigDecimal(payable))
                .amountPaid(new BigDecimal(paid))
                .completedAt(completedAt)
                .build());
    }

    private Map<Long, BigDecimal> asMap(List<PurchaseOrderRepository.SupplierAmount> rows) {
        return rows.stream().collect(Collectors.toMap(
                PurchaseOrderRepository.SupplierAmount::getSupplierId,
                PurchaseOrderRepository.SupplierAmount::getAmount));
    }

    @Test
    void sumPurchasedBySupplier_allTime_countsCompletedReceiptsOfThisStoreOnly() {
        Map<Long, BigDecimal> totals = asMap(purchaseOrderRepository.sumPurchasedBySupplier(
                storeId, PurchaseOrderStatus.COMPLETED,
                LocalDateTime.of(1970, 1, 1, 0, 0), LocalDateTime.of(9999, 12, 31, 23, 59, 59)));

        // 2.400.000 + 600.000; the 999.999 draft is not a purchase yet.
        assertThat(totals.get(nuocSach.getId())).isEqualByComparingTo("3000000");
        assertThat(totals.get(bepGas.getId())).isEqualByComparingTo("500000");
        assertThat(totals).hasSize(2);
    }

    @Test
    void sumPurchasedBySupplier_narrowsToTheDateRange() {
        Map<Long, BigDecimal> september = asMap(purchaseOrderRepository.sumPurchasedBySupplier(
                storeId, PurchaseOrderStatus.COMPLETED,
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 30, 23, 59, 59)));

        assertThat(september.get(nuocSach.getId())).isEqualByComparingTo("2400000");
        assertThat(september.get(bepGas.getId())).isEqualByComparingTo("500000");

        Map<Long, BigDecimal> august = asMap(purchaseOrderRepository.sumPurchasedBySupplier(
                storeId, PurchaseOrderStatus.COMPLETED,
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59, 59)));

        assertThat(august.get(nuocSach.getId())).isEqualByComparingTo("600000");
        assertThat(august).doesNotContainKey(bepGas.getId());
    }

    @Test
    void sumDebtBySupplier_isWhatIsStillOwedAcrossEveryCompletedReceipt() {
        Map<Long, BigDecimal> debts = asMap(
                purchaseOrderRepository.sumDebtBySupplier(storeId, PurchaseOrderStatus.COMPLETED));

        // (2.400.000 - 1.000.000) + (600.000 - 600.000)
        assertThat(debts.get(nuocSach.getId())).isEqualByComparingTo("1400000");
        assertThat(debts.get(bepGas.getId())).isEqualByComparingTo("500000");
    }

    @Test
    void countBySupplierId_isWhatStandsBetweenXoaAndNgungHoatDong() {
        assertThat(purchaseOrderRepository.countBySupplierId(nuocSach.getId())).isEqualTo(3);
        assertThat(purchaseOrderRepository.countBySupplierId(bepGas.getId())).isEqualTo(1);
    }
}

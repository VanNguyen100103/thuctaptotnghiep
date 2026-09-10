package com.ut.edu.backend.supplier;

import com.ut.edu.backend.purchaseorder.PurchaseOrderRepository;
import com.ut.edu.backend.purchaseorder.PurchaseOrderStatus;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the Nhà cung cấp list's own filtering - which happens in this
 * controller rather than in SQL - and the one rule "Xóa" enforces that
 * "Ngừng hoạt động" does not.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierControllerTest {

    private static final Long STORE_ID = 7L;

    @Mock private SupplierRepository supplierRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private TenantGuard tenantGuard;
    @Mock private SubscriptionGuard subscriptionGuard;

    @InjectMocks
    private SupplierController controller;

    /** Two active suppliers and one stopped, with one of the active pair carrying receipts. */
    private Supplier nuocSach;
    private Supplier bepGas;
    private Supplier stopped;

    @BeforeEach
    void setUp() {
        when(tenantGuard.requireStore()).thenReturn(STORE_ID);

        nuocSach = supplier(1L, "NCC000001", "Nước sạch", "19004600", "Đồ uống", true);
        bepGas = supplier(2L, "NCC000002", "Bếp gas Miền Nam", "19006522", "Thiết bị", true);
        stopped = supplier(3L, "NCC000003", "Cũ không dùng nữa", "0900000000", null, false);

        when(supplierRepository.findAllByOrderByNameAsc()).thenReturn(List.of(bepGas, nuocSach, stopped));
        when(purchaseOrderRepository.sumPurchasedBySupplier(anyLong(), any(), any(), any()))
                .thenReturn(List.of(amount(1L, "2400000"), amount(2L, "600000")));
        when(purchaseOrderRepository.sumDebtBySupplier(anyLong(), any()))
                .thenReturn(List.of(amount(1L, "1000000")));
    }

    private static Supplier supplier(Long id, String code, String name, String phone, String group, boolean active) {
        return Supplier.builder()
                .id(id)
                .code(code)
                .name(name)
                .phone(phone)
                .groupName(group)
                .note("ghi chú " + code)
                .active(active)
                .build();
    }

    private static PurchaseOrderRepository.SupplierAmount amount(Long supplierId, String value) {
        return new PurchaseOrderRepository.SupplierAmount() {
            @Override
            public Long getSupplierId() {
                return supplierId;
            }

            @Override
            public BigDecimal getAmount() {
                return new BigDecimal(value);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<SupplierResponse> suppliers(ResponseEntity<?> response) {
        return (List<SupplierResponse>) body(response).get("suppliers");
    }

    private ResponseEntity<?> list(String query, String group, String status,
                                   BigDecimal totalFrom, BigDecimal totalTo,
                                   BigDecimal debtFrom, BigDecimal debtTo) {
        return controller.list(query, null, null, group, status, totalFrom, totalTo, debtFrom, debtTo,
                null, null, 0, 15);
    }

    @Test
    void list_defaultsToActiveSuppliers_withReceiptTotalsAttached() {
        ResponseEntity<?> response = list(null, null, "active", null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<SupplierResponse> rows = suppliers(response);
        assertThat(rows).extracting(SupplierResponse::code).containsExactly("NCC000002", "NCC000001");
        assertThat(rows).extracting(SupplierResponse::totalPurchase)
                .containsExactly(new BigDecimal("600000"), new BigDecimal("2400000"));
        // Only NCC000001 owes anything; a supplier with no debt row reads zero, not null.
        assertThat(rows).extracting(SupplierResponse::currentDebt)
                .containsExactly(BigDecimal.ZERO, new BigDecimal("1000000"));
    }

    @Test
    void list_totalsRow_sumsEverySupplierMatchingTheFilters() {
        Map<String, Object> body = body(list(null, null, "active", null, null, null, null));

        assertThat(body.get("totalItems")).isEqualTo(2);
        assertThat(body.get("totalPurchaseSum")).isEqualTo(new BigDecimal("3000000"));
        assertThat(body.get("totalDebtSum")).isEqualTo(new BigDecimal("1000000"));
    }

    @Test
    void list_inactiveStatus_returnsOnlyStoppedSuppliers() {
        assertThat(suppliers(list(null, null, "inactive", null, null, null, null)))
                .extracting(SupplierResponse::code)
                .containsExactly("NCC000003");
    }

    @Test
    void list_allStatus_returnsBoth() {
        assertThat(suppliers(list(null, null, "all", null, null, null, null))).hasSize(3);
    }

    @Test
    void list_keyword_ignoresVietnameseDiacritics() {
        assertThat(suppliers(list("nuoc", null, "active", null, null, null, null)))
                .extracting(SupplierResponse::code)
                .containsExactly("NCC000001");
    }

    @Test
    void list_keyword_alsoMatchesCodeAndPhone() {
        assertThat(suppliers(list("19006522", null, "active", null, null, null, null)))
                .extracting(SupplierResponse::code)
                .containsExactly("NCC000002");
        assertThat(suppliers(list("NCC000001", null, "active", null, null, null, null)))
                .extracting(SupplierResponse::code)
                .containsExactly("NCC000001");
    }

    @Test
    void list_group_narrowsToThatGroupOnly() {
        assertThat(suppliers(list(null, "Đồ uống", "active", null, null, null, null)))
                .extracting(SupplierResponse::code)
                .containsExactly("NCC000001");
    }

    @Test
    void list_totalPurchaseRange_appliesToTheRolledUpColumn() {
        assertThat(suppliers(list(null, null, "active", new BigDecimal("1000000"), null, null, null)))
                .extracting(SupplierResponse::code)
                .containsExactly("NCC000001");
        assertThat(suppliers(list(null, null, "active", null, new BigDecimal("1000000"), null, null)))
                .extracting(SupplierResponse::code)
                .containsExactly("NCC000002");
    }

    @Test
    void list_debtRange_keepsSuppliersWithNoDebtOutOfAPositiveFloor() {
        assertThat(suppliers(list(null, null, "active", null, null, new BigDecimal("1"), null)))
                .extracting(SupplierResponse::code)
                .containsExactly("NCC000001");
    }

    @Test
    void list_timeRange_narrowsPurchasesButNeverTheDebt() {
        controller.list(null, null, null, null, "active", null, null, null, null,
                "2026-09-01", "2026-09-30", 0, 15);

        verify(purchaseOrderRepository).sumPurchasedBySupplier(
                eq(STORE_ID),
                eq(PurchaseOrderStatus.COMPLETED),
                eq(LocalDateTime.of(2026, 9, 1, 0, 0)),
                any());
        // The debt query takes no range at all - what is owed is owed today.
        verify(purchaseOrderRepository).sumDebtBySupplier(STORE_ID, PurchaseOrderStatus.COMPLETED);
    }

    @Test
    void delete_refusesASupplierThatAlreadyAppearsOnAReceipt() {
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(nuocSach));
        when(tenantGuard.isCurrentStore(any())).thenReturn(true);
        when(purchaseOrderRepository.countBySupplierId(1L)).thenReturn(3L);

        ResponseEntity<?> response = controller.delete(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(String.valueOf(body(response).get("error"))).contains("Ngừng hoạt động");
        verify(supplierRepository, never()).delete(any());
    }

    @Test
    void delete_removesASupplierNoReceiptPointsAt() {
        when(supplierRepository.findById(3L)).thenReturn(Optional.of(stopped));
        when(tenantGuard.isCurrentStore(any())).thenReturn(true);
        when(purchaseOrderRepository.countBySupplierId(3L)).thenReturn(0L);

        ResponseEntity<?> response = controller.delete(3L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(supplierRepository).delete(stopped);
    }

    @Test
    void setActive_stopsAndRestartsWithoutTouchingAnythingElse() {
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(nuocSach));
        when(tenantGuard.isCurrentStore(any())).thenReturn(true);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.setActive(1L, Map.of("active", false));
        assertThat(nuocSach.getActive()).isFalse();

        controller.setActive(1L, Map.of("active", true));
        assertThat(nuocSach.getActive()).isTrue();
    }
}

package com.ut.edu.backend.purchaseorder;

import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.product.ProductRepository;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.supplier.SupplierRepository;
import com.ut.edu.backend.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseOrderServiceTest {

    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private ProductRepository productRepository;
    @Mock private TenantGuard tenantGuard;

    @InjectMocks
    private PurchaseOrderService purchaseOrderService;

    private Store store;
    private Product product;
    private User user;

    @BeforeEach
    void setUp() {
        store = Store.builder().id(1L).name("Shop A").slug("shop-a").build();
        product = Product.builder().id(100L).name("Áo thun").sku("AT001")
                .price(new BigDecimal("150000")).stockQuantity(10).store(store).build();
        user = User.builder().id(5L).username("owner1").build();

        when(tenantGuard.currentStoreRef()).thenReturn(store);
        when(tenantGuard.isCurrentStore(store)).thenReturn(true);
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_happyPath_computesTotalsAndGeneratesSequentialCode() {
        when(purchaseOrderRepository.countByStoreId(1L)).thenReturn(3L);

        SavePurchaseOrderRequest request = new SavePurchaseOrderRequest(
                null,
                new BigDecimal("20000"),  // discountAmount (order-level, "Giảm giá")
                new BigDecimal("30000"),  // supplierChargeAmount ("Chi phí nhập trả NCC") - ADDS to payableAmount
                new BigDecimal("50000"),  // amountPaid ("Tiền trả nhà cung cấp") - must NOT affect payableAmount, only debtAmount
                new BigDecimal("15000"),  // otherCosts - must NOT affect payableAmount either
                "note",
                List.of(new PurchaseOrderItemRequest(100L, 5, new BigDecimal("100000"), new BigDecimal("10000"))));

        PurchaseOrder saved = purchaseOrderService.create(1L, user, request);

        assertThat(saved.getCode()).isEqualTo("PN000004"); // 4th purchase order for this store
        assertThat(saved.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(saved.getTotalGoodsValue()).isEqualByComparingTo("490000"); // 5*100000 - 10000
        assertThat(saved.getPayableAmount()).isEqualByComparingTo("500000"); // 490000 - 20000 + 30000, amountPaid/otherCosts excluded
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getProductName()).isEqualTo("Áo thun");
        assertThat(saved.getItems().get(0).getProductSku()).isEqualTo("AT001");
    }

    @Test
    void create_unknownProduct_throwsAndNeverSaves() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());
        SavePurchaseOrderRequest request = new SavePurchaseOrderRequest(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                List.of(new PurchaseOrderItemRequest(999L, 1, BigDecimal.TEN, BigDecimal.ZERO)));

        assertThatThrownBy(() -> purchaseOrderService.create(1L, user, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");

        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void update_nonDraft_isRejected() {
        PurchaseOrder completed = PurchaseOrder.builder().id(10L).store(store).status(PurchaseOrderStatus.COMPLETED).build();
        SavePurchaseOrderRequest request = new SavePurchaseOrderRequest(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of());

        assertThatThrownBy(() -> purchaseOrderService.update(completed, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Phiếu tạm");

        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void complete_incrementsStockAndRefreshesCostPriceAndLocksStatus() {
        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .product(product).productName("Áo thun").productSku("AT001")
                .quantity(5).unitPrice(new BigDecimal("120000")).discountAmount(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("600000")).build();
        PurchaseOrder draft = PurchaseOrder.builder().id(20L).store(store).status(PurchaseOrderStatus.DRAFT).build();
        draft.addItem(item);

        when(productRepository.findByIdWithLock(100L)).thenReturn(Optional.of(product));

        PurchaseOrder saved = purchaseOrderService.complete(draft);

        assertThat(product.getStockQuantity()).isEqualTo(15); // 10 (initial) + 5 received
        assertThat(product.getCostPrice()).isEqualByComparingTo("120000"); // last-cost basis
        assertThat(saved.getStatus()).isEqualTo(PurchaseOrderStatus.COMPLETED);
        assertThat(saved.getCompletedAt()).isNotNull();
    }

    @Test
    void complete_withNoItems_isRejected() {
        PurchaseOrder draft = PurchaseOrder.builder().id(21L).store(store).status(PurchaseOrderStatus.DRAFT).build();

        assertThatThrownBy(() -> purchaseOrderService.complete(draft))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chưa có hàng hóa");

        verify(productRepository, never()).findByIdWithLock(any());
    }

    @Test
    void complete_alreadyCompleted_isRejected() {
        PurchaseOrder completed = PurchaseOrder.builder().id(22L).store(store).status(PurchaseOrderStatus.COMPLETED).build();

        assertThatThrownBy(() -> purchaseOrderService.complete(completed)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancel_draftBecomesCancelled() {
        PurchaseOrder draft = PurchaseOrder.builder().id(23L).store(store).status(PurchaseOrderStatus.DRAFT).build();

        PurchaseOrder saved = purchaseOrderService.cancel(draft);

        assertThat(saved.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
    }

    @Test
    void cancel_completedOrder_isRejected() {
        PurchaseOrder completed = PurchaseOrder.builder().id(24L).store(store).status(PurchaseOrderStatus.COMPLETED).build();

        assertThatThrownBy(() -> purchaseOrderService.cancel(completed)).isInstanceOf(IllegalStateException.class);
        verify(purchaseOrderRepository, never()).save(any());
    }
}

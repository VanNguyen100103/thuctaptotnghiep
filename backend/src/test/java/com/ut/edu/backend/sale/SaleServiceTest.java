package com.ut.edu.backend.sale;

import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.product.ProductRepository;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
class SaleServiceTest {

    @Mock private SaleRepository saleRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private TenantGuard tenantGuard;

    @InjectMocks
    private SaleService saleService;

    private Store store;
    private Product product;
    private User cashier;

    @BeforeEach
    void setUp() {
        store = Store.builder().id(1L).name("Shop A").slug("shop-a").build();
        product = Product.builder().id(100L).name("Sữa tươi Ba Vì").sku("SP652363")
                .price(new BigDecimal("320000")).stockQuantity(10).soldCount(0).store(store).build();
        cashier = User.builder().id(5L).username("cashier1").build();

        when(tenantGuard.currentStoreRef()).thenReturn(store);
        when(tenantGuard.isCurrentStore(store)).thenReturn(true);
        when(productRepository.findByIdWithLock(100L)).thenReturn(Optional.of(product));
        when(saleRepository.save(any(Sale.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void checkout_singleTender_computesTotalsDecrementsStockAndGeneratesSequentialCode() {
        when(saleRepository.countByStoreId(1L)).thenReturn(2L);

        CreateSaleRequest request = new CreateSaleRequest(
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                List.of(new SaleItemRequest(100L, 1, null, null)),
                List.of(new SalePaymentRequest(SalePaymentMethod.CASH, new BigDecimal("320000"))));

        Sale saved = saleService.checkout(1L, cashier, request);

        assertThat(saved.getCode()).isEqualTo("HD000003"); // 3rd sale for this store
        assertThat(saved.getSubtotal()).isEqualByComparingTo("320000");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("320000");
        assertThat(saved.getAmountReceived()).isEqualByComparingTo("320000");
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getUnitPrice()).isEqualByComparingTo("320000"); // defaulted from product.price
        assertThat(product.getStockQuantity()).isEqualTo(9);
        assertThat(product.getSoldCount()).isEqualTo(1);
    }

    @Test
    void checkout_splitTender_sumsAcrossPaymentLines() {
        when(saleRepository.countByStoreId(1L)).thenReturn(0L);

        CreateSaleRequest request = new CreateSaleRequest(
                null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                List.of(new SaleItemRequest(100L, 1, new BigDecimal("320000"), null)),
                List.of(
                        new SalePaymentRequest(SalePaymentMethod.CASH, new BigDecimal("300000")),
                        new SalePaymentRequest(SalePaymentMethod.BANK_TRANSFER, new BigDecimal("20000"))));

        Sale saved = saleService.checkout(1L, cashier, request);

        assertThat(saved.getPayments()).hasSize(2);
        assertThat(saved.getAmountReceived()).isEqualByComparingTo("320000");
        assertThat(SaleResponse.from(saved).changeAmount()).isEqualByComparingTo("0");
    }

    @Test
    void checkout_overpaidCash_reportsChangeButStillSucceeds() {
        when(saleRepository.countByStoreId(1L)).thenReturn(0L);

        CreateSaleRequest request = new CreateSaleRequest(
                null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                List.of(new SaleItemRequest(100L, 1, new BigDecimal("320000"), null)),
                List.of(new SalePaymentRequest(SalePaymentMethod.CASH, new BigDecimal("500000"))));

        Sale saved = saleService.checkout(1L, cashier, request);

        assertThat(SaleResponse.from(saved).changeAmount()).isEqualByComparingTo("180000");
    }

    @Test
    void checkout_underpaid_isRejectedAndNeverSaved() {
        CreateSaleRequest request = new CreateSaleRequest(
                null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                List.of(new SaleItemRequest(100L, 1, new BigDecimal("320000"), null)),
                List.of(new SalePaymentRequest(SalePaymentMethod.CASH, new BigDecimal("300000"))));

        assertThatThrownBy(() -> saleService.checkout(1L, cashier, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chưa đủ");

        verify(saleRepository, never()).save(any());
    }

    @Test
    void checkout_insufficientStock_isRejected() {
        CreateSaleRequest request = new CreateSaleRequest(
                null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                List.of(new SaleItemRequest(100L, 99, null, null)),
                List.of(new SalePaymentRequest(SalePaymentMethod.CASH, new BigDecimal("999999999"))));

        assertThatThrownBy(() -> saleService.checkout(1L, cashier, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không đủ tồn kho");

        verify(saleRepository, never()).save(any());
    }

    @Test
    void checkout_unknownProduct_throwsAndNeverSaves() {
        when(productRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());
        CreateSaleRequest request = new CreateSaleRequest(
                null, BigDecimal.ZERO, BigDecimal.ZERO, null,
                List.of(new SaleItemRequest(999L, 1, BigDecimal.TEN, null)),
                List.of(new SalePaymentRequest(SalePaymentMethod.CASH, BigDecimal.TEN)));

        assertThatThrownBy(() -> saleService.checkout(1L, cashier, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");

        verify(saleRepository, never()).save(any());
    }

    @Test
    void checkout_discountAndOtherCollection_adjustTotal() {
        when(saleRepository.countByStoreId(1L)).thenReturn(0L);

        CreateSaleRequest request = new CreateSaleRequest(
                null,
                new BigDecimal("20000"),  // discountAmount
                new BigDecimal("5000"),   // otherCollectionAmount
                null,
                List.of(new SaleItemRequest(100L, 1, new BigDecimal("320000"), null)),
                List.of(new SalePaymentRequest(SalePaymentMethod.CASH, new BigDecimal("305000"))));

        Sale saved = saleService.checkout(1L, cashier, request);

        assertThat(saved.getSubtotal()).isEqualByComparingTo("320000");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("305000"); // 320000 - 20000 + 5000
    }
}

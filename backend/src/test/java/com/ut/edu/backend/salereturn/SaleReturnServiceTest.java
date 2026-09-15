package com.ut.edu.backend.salereturn;

import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.product.ProductRepository;
import com.ut.edu.backend.sale.Customer;
import com.ut.edu.backend.sale.CustomerRepository;
import com.ut.edu.backend.sale.Sale;
import com.ut.edu.backend.sale.SaleItem;
import com.ut.edu.backend.sale.SalePaymentMethod;
import com.ut.edu.backend.sale.SaleRepository;
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
class SaleReturnServiceTest {

    @Mock private SaleReturnRepository saleReturnRepository;
    @Mock private SaleRepository saleRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private TenantGuard tenantGuard;

    @InjectMocks
    private SaleReturnService saleReturnService;

    private Store store;
    private Product product;
    private User cashier;
    private SaleItem soldLine;
    private Sale sale;

    @BeforeEach
    void setUp() {
        store = Store.builder().id(1L).name("Shop A").slug("shop-a").build();
        product = Product.builder().id(100L).name("Sữa tươi Ba Vì").sku("SP652363")
                .price(new BigDecimal("100000")).stockQuantity(4).soldCount(6).store(store).build();
        cashier = User.builder().id(5L).username("cashier1").build();

        // HD000001: 3 units at 100,000, no discounts anywhere - the plain case
        // the other tests bend one figure at a time away from.
        soldLine = SaleItem.builder().id(50L).product(product)
                .productName(product.getName()).productSku(product.getSku())
                .quantity(3).unitPrice(new BigDecimal("100000"))
                .discountAmount(BigDecimal.ZERO).lineTotal(new BigDecimal("300000"))
                .build();
        sale = newSale();

        when(tenantGuard.currentStoreRef()).thenReturn(store);
        when(tenantGuard.isCurrentStore(store)).thenReturn(true);
        when(saleRepository.findById(10L)).thenReturn(Optional.of(sale));
        when(productRepository.findByIdWithLock(100L)).thenReturn(Optional.of(product));
        when(saleReturnRepository.save(any(SaleReturn.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Sale newSale() {
        Sale s = Sale.builder().id(10L).code("HD000001").store(store)
                .subtotal(new BigDecimal("300000"))
                .discountAmount(BigDecimal.ZERO)
                .couponDiscountAmount(BigDecimal.ZERO)
                .pointsRedeemed(0).pointsRedeemedAmount(BigDecimal.ZERO)
                .pointsEarned(0)
                .totalAmount(new BigDecimal("300000"))
                .amountReceived(new BigDecimal("300000"))
                .build();
        s.addItem(soldLine);
        return s;
    }

    private CreateSaleReturnRequest returnOf(int quantity, BigDecimal returnFee) {
        return new CreateSaleReturnRequest(10L, returnFee, SalePaymentMethod.BANK_TRANSFER, null,
                List.of(new SaleReturnItemRequest(50L, quantity)));
    }

    @Test
    void create_wholeLine_refundsWhatWasPaidRestocksAndGeneratesSequentialCode() {
        when(saleReturnRepository.countByStoreId(1L)).thenReturn(2L);

        SaleReturn saved = saleReturnService.create(1L, cashier, returnOf(3, null));

        assertThat(saved.getCode()).isEqualTo("TH000003"); // 3rd return for this store
        assertThat(saved.getTotalGoodsValue()).isEqualByComparingTo("300000");
        assertThat(saved.getRefundAmount()).isEqualByComparingTo("300000");
        assertThat(saved.getRefundMethod()).isEqualTo(SalePaymentMethod.BANK_TRANSFER);
        assertThat(saved.getSale()).isSameAs(sale);
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getSaleItem()).isSameAs(soldLine);
        assertThat(product.getStockQuantity()).isEqualTo(7); // 4 back up by 3
    }

    @Test
    void create_partOfALine_proratesThatLineOwnDiscount() {
        // The line sold 3 for 300,000 with 30,000 off; one unit back is worth
        // 100,000 less a third of that discount.
        soldLine.setDiscountAmount(new BigDecimal("30000"));
        soldLine.setLineTotal(new BigDecimal("270000"));
        sale.setSubtotal(new BigDecimal("270000"));

        SaleReturn saved = saleReturnService.create(1L, cashier, returnOf(1, null));

        assertThat(saved.getItems().get(0).getDiscountAmount()).isEqualByComparingTo("10000");
        assertThat(saved.getItems().get(0).getLineTotal()).isEqualByComparingTo("90000");
        assertThat(saved.getTotalGoodsValue()).isEqualByComparingTo("90000");
        assertThat(saved.getRefundAmount()).isEqualByComparingTo("90000");
        assertThat(product.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void create_invoiceLevelDiscountAndCoupon_comeOffTheRefundInProportion() {
        // 300,000 of goods that collected only 240,000 (30,000 off the invoice
        // + a 30,000 coupon). A third of the goods back is a third of that gap.
        sale.setDiscountAmount(new BigDecimal("30000"));
        sale.setCouponDiscountAmount(new BigDecimal("30000"));
        sale.setTotalAmount(new BigDecimal("240000"));

        SaleReturn saved = saleReturnService.create(1L, cashier, returnOf(1, null));

        assertThat(saved.getTotalGoodsValue()).isEqualByComparingTo("100000");
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("20000");
        assertThat(saved.getRefundAmount()).isEqualByComparingTo("80000"); // what that unit actually brought in
    }

    @Test
    void create_returnFee_isWithheldFromTheRefund() {
        SaleReturn saved = saleReturnService.create(1L, cashier, returnOf(1, new BigDecimal("15000")));

        assertThat(saved.getReturnFee()).isEqualByComparingTo("15000");
        assertThat(saved.getRefundAmount()).isEqualByComparingTo("85000");
    }

    @Test
    void create_returnFeeAboveTheGoods_isRejected() {
        assertThatThrownBy(() -> saleReturnService.create(1L, cashier, returnOf(1, new BigDecimal("120000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phí trả hàng");
        assertThat(product.getStockQuantity()).isEqualTo(4); // nothing restocked
    }

    @Test
    void create_pointsRedeemedComeBackAndPointsEarnedAreClawedBack() {
        Customer customer = Customer.builder().id(7L).code("KH000002").name("Nguyễn Văn B")
                .store(store).loyaltyPoints(50).build();
        sale.setCustomer(customer);
        sale.setPointsRedeemed(30);
        sale.setPointsRedeemedAmount(new BigDecimal("30000"));
        sale.setPointsEarned(27);
        sale.setTotalAmount(new BigDecimal("270000"));

        SaleReturn saved = saleReturnService.create(1L, cashier, returnOf(1, null));

        assertThat(saved.getPointsRestored()).isEqualTo(10); // a third of 30
        assertThat(saved.getPointsReverted()).isEqualTo(9);  // a third of 27
        assertThat(customer.getLoyaltyPoints()).isEqualTo(51); // 50 + 10 - 9
        assertThat(saved.getCustomer()).isSameAs(customer);
        // The points the customer spent on this unit are not refunded as cash
        // as well - they went back as points.
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("10000");
        assertThat(saved.getRefundAmount()).isEqualByComparingTo("90000");
        verify(customerRepository).save(customer);
    }

    @Test
    void create_pointsNeverExceedWhatEarlierReturnsLeft() {
        Customer customer = Customer.builder().id(7L).code("KH000002").name("Nguyễn Văn B")
                .store(store).loyaltyPoints(50).build();
        sale.setCustomer(customer);
        sale.setPointsEarned(27);
        when(saleReturnRepository.sumPointsRevertedBySale(10L)).thenReturn(25);

        SaleReturn saved = saleReturnService.create(1L, cashier, returnOf(3, null));

        assertThat(saved.getPointsReverted()).isEqualTo(2); // 27 earned, 25 already taken
    }

    @Test
    void create_moreThanTheLineHasLeft_isRejected() {
        when(saleReturnRepository.sumReturnedQuantitiesBySale(10L))
                .thenReturn(List.of(returnedQuantity(50L, 2L)));

        assertThatThrownBy(() -> saleReturnService.create(1L, cashier, returnOf(2, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chỉ còn 1");
        assertThat(product.getStockQuantity()).isEqualTo(4);
    }

    @Test
    void create_sameLineTwiceInOneDocument_isRejected() {
        CreateSaleReturnRequest request = new CreateSaleReturnRequest(
                10L, null, SalePaymentMethod.CASH, null,
                List.of(new SaleReturnItemRequest(50L, 1), new SaleReturnItemRequest(50L, 1)));

        assertThatThrownBy(() -> saleReturnService.create(1L, cashier, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("một lần");
    }

    @Test
    void create_lineFromAnotherInvoice_isRejected() {
        CreateSaleReturnRequest request = new CreateSaleReturnRequest(
                10L, null, SalePaymentMethod.CASH, null,
                List.of(new SaleReturnItemRequest(999L, 1)));

        assertThatThrownBy(() -> saleReturnService.create(1L, cashier, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không có dòng hàng này");
    }

    @Test
    void create_invoiceOfAnotherStore_looksMissing() {
        Store other = Store.builder().id(2L).name("Shop B").slug("shop-b").build();
        sale.setStore(other);
        when(tenantGuard.isCurrentStore(other)).thenReturn(false);

        assertThatThrownBy(() -> saleReturnService.create(1L, cashier, returnOf(1, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy hóa đơn");
    }

    @Test
    void create_deletedProduct_stillRefundsButCannotRestock() {
        soldLine.setProduct(null);

        SaleReturn saved = saleReturnService.create(1L, cashier, returnOf(1, null));

        assertThat(saved.getRefundAmount()).isEqualByComparingTo("100000");
        assertThat(saved.getItems().get(0).getProductName()).isEqualTo("Sữa tươi Ba Vì"); // the snapshot carries it
        verify(productRepository, never()).findByIdWithLock(any());
    }

    @Test
    void create_bankTransferRefund_startsOwedToTheCustomer() {
        SaleReturn saved = saleReturnService.create(1L, cashier, returnOf(1, null));

        assertThat(saved.getRefundStatus()).isEqualTo(SaleReturnRefundStatus.PENDING);
        assertThat(saved.isAwaitingTransfer()).isTrue();
        assertThat(saved.getRefundedAt()).isNull();
    }

    @Test
    void create_cashRefund_isSettledAtTheCounter() {
        CreateSaleReturnRequest request = new CreateSaleReturnRequest(
                10L, null, SalePaymentMethod.CASH, null, List.of(new SaleReturnItemRequest(50L, 1)));

        SaleReturn saved = saleReturnService.create(1L, cashier, request);

        assertThat(saved.getRefundStatus()).isEqualTo(SaleReturnRefundStatus.REFUNDED);
        assertThat(saved.getRefundedAt()).isNotNull();
    }

    @Test
    void create_transferOfNothing_hasNoTransferToWaitFor() {
        // The fee ate the whole refund, so there is no money to send and
        // nothing to chase - parking this in PENDING would invent a debt.
        SaleReturn saved = saleReturnService.create(1L, cashier, returnOf(1, new BigDecimal("100000")));

        assertThat(saved.getRefundAmount()).isEqualByComparingTo("0");
        assertThat(saved.getRefundStatus()).isEqualTo(SaleReturnRefundStatus.REFUNDED);
    }

    @Test
    void settleRefundFromWebhook_marksItPaidAndKeepsSePayReference() {
        SaleReturn pending = pendingReturn(new BigDecimal("86000"));

        saleReturnService.settleRefundFromWebhook(9L, new BigDecimal("86000"), "FT2609");

        assertThat(pending.getRefundStatus()).isEqualTo(SaleReturnRefundStatus.REFUNDED);
        assertThat(pending.getRefundReference()).isEqualTo("FT2609");
        assertThat(pending.getRefundedAt()).isNotNull();
    }

    @Test
    void settleRefundFromWebhook_shortTransfer_leavesItOwed() {
        SaleReturn pending = pendingReturn(new BigDecimal("86000"));

        saleReturnService.settleRefundFromWebhook(9L, new BigDecimal("50000"), "FT2609");

        // The customer is still owed 36,000 - calling this settled is the one
        // outcome nobody can spot again by looking at the screen.
        assertThat(pending.getRefundStatus()).isEqualTo(SaleReturnRefundStatus.PENDING);
        assertThat(pending.getRefundReference()).isNull();
    }

    @Test
    void settleRefundFromWebhook_secondTransfer_doesNotOverwriteTheFirst() {
        SaleReturn pending = pendingReturn(new BigDecimal("86000"));
        saleReturnService.settleRefundFromWebhook(9L, new BigDecimal("86000"), "FT-first");

        saleReturnService.settleRefundFromWebhook(9L, new BigDecimal("86000"), "FT-second");

        assertThat(pending.getRefundReference()).isEqualTo("FT-first");
    }

    @Test
    void markRefundedByHand_settlesAReceiptNoWebhookWillEverReach() {
        SaleReturn pending = pendingReturn(new BigDecimal("86000"));

        saleReturnService.markRefundedByHand(pending);

        assertThat(pending.getRefundStatus()).isEqualTo(SaleReturnRefundStatus.REFUNDED);
        assertThat(pending.getRefundedAt()).isNotNull();
        assertThat(pending.getRefundReference()).isNull(); // no SePay transaction behind it
    }

    @Test
    void transferContent_isTheShortCodeTypedIntoTheTransfer() {
        SaleReturn pending = pendingReturn(new BigDecimal("86000"));

        assertThat(pending.transferContent()).isEqualTo("TH9");
    }

    /** A receipt already saved and still owed - what the webhook and the manual tick both act on. */
    private SaleReturn pendingReturn(BigDecimal refundAmount) {
        SaleReturn pending = SaleReturn.builder()
                .id(9L).code("TH000009").store(store).sale(sale)
                .refundMethod(SalePaymentMethod.BANK_TRANSFER)
                .refundAmount(refundAmount)
                .refundStatus(SaleReturnRefundStatus.PENDING)
                .build();
        when(saleReturnRepository.findById(9L)).thenReturn(Optional.of(pending));
        return pending;
    }

    @Test
    void findReturnable_reportsWhatEachLineHasLeft() {
        when(saleReturnRepository.sumReturnedQuantitiesBySale(10L))
                .thenReturn(List.of(returnedQuantity(50L, 1L)));

        ReturnableSaleResponse returnable = saleReturnService.findReturnable(10L);

        assertThat(returnable.saleCode()).isEqualTo("HD000001");
        assertThat(returnable.lines()).hasSize(1);
        assertThat(returnable.lines().get(0).soldQuantity()).isEqualTo(3);
        assertThat(returnable.lines().get(0).returnedQuantity()).isEqualTo(1);
        assertThat(returnable.lines().get(0).returnableQuantity()).isEqualTo(2);
    }

    private static SaleReturnRepository.ReturnedQuantity returnedQuantity(Long saleItemId, Long quantity) {
        return new SaleReturnRepository.ReturnedQuantity() {
            @Override
            public Long getSaleItemId() {
                return saleItemId;
            }

            @Override
            public Long getQuantity() {
                return quantity;
            }
        };
    }
}

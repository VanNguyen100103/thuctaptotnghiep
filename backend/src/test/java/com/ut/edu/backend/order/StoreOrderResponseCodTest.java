package com.ut.edu.backend.order;

import com.ut.edu.backend.sale.Sale;
import com.ut.edu.backend.shipping.goship.Shipment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Khách đã trả" on a COD order, which is money the shop does not have yet.
 *
 * This used to key on the order sitting at PENDING_COD, and that worked only
 * because nothing ever moved a register order off it. Once the carrier began
 * driving the status, an order went to PROCESSING minutes after pickup and its
 * whole COD balance started reading as collected - while it was still in a
 * courier's bag.
 */
class StoreOrderResponseCodTest {

    private static Order deliveryOrder(OrderStatus status, BigDecimal codAmount) {
        Sale sale = Sale.builder()
                .id(1L).code("HD000024")
                .amountReceived(new BigDecimal("65000"))
                .build();
        Order order = Order.builder()
                .id(1L).orderNumber("DH000004").status(status)
                .salesChannel(SalesChannel.POS_DELIVERY)
                .sale(sale)
                .subtotal(new BigDecimal("22000"))
                .shippingCost(new BigDecimal("43000"))
                .total(new BigDecimal("65000"))
                .build();
        if (codAmount != null) {
            order.getShipments().add(Shipment.builder()
                    .id(9L).orderRef("TT1789").order(order)
                    .codAmount(codAmount).shippingFee(new BigDecimal("43000"))
                    .toAddress("394 Võ Nguyên Giáp").toWardName("Nhơn Hoà")
                    .toDistrictName("An Nhơn").toCityName("Bình Định")
                    .build());
        }
        return order;
    }

    private static BigDecimal paid(Order order) {
        return StoreOrderResponse.summary(order).amountPaid();
    }

    @Test
    void moneyStillWithTheCourierIsNotMoneyTheShopHas() {
        // The exact case from the screenshot: carrier picked it up, the sync
        // moved the order to PROCESSING, COD 65.000 is in a courier's bag.
        assertThat(paid(deliveryOrder(OrderStatus.PROCESSING, new BigDecimal("65000"))))
                .isEqualByComparingTo("0");
        assertThat(paid(deliveryOrder(OrderStatus.SHIPPED, new BigDecimal("65000"))))
                .isEqualByComparingTo("0");
    }

    @Test
    void deliverySettlesIt() {
        assertThat(paid(deliveryOrder(OrderStatus.DELIVERED, new BigDecimal("65000"))))
                .isEqualByComparingTo("65000");
    }

    @Test
    void aParcelWithNoCodWasAlreadyPaidAtTheTill() {
        // Paid by transfer at the register, then shipped. The shop has the money
        // from the moment of sale, however far along the parcel is.
        assertThat(paid(deliveryOrder(OrderStatus.PROCESSING, BigDecimal.ZERO)))
                .isEqualByComparingTo("65000");
    }

    @Test
    void selfDeliveryWithNoBookingStillHonoursPendingCod() {
        // No shipment to read a COD figure from, so the status is all there is.
        assertThat(paid(deliveryOrder(OrderStatus.PENDING_COD, null))).isEqualByComparingTo("0");
        assertThat(paid(deliveryOrder(OrderStatus.DELIVERED, null))).isEqualByComparingTo("65000");
    }

    @Test
    void changeHandedBackIsNotMoneyTheShopKept() {
        Sale overpaid = Sale.builder().id(1L).code("HD1").amountReceived(new BigDecimal("100000")).build();
        Order order = Order.builder()
                .id(2L).orderNumber("DH1").status(OrderStatus.DELIVERED)
                .sale(overpaid).total(new BigDecimal("65000"))
                .build();

        assertThat(paid(order)).isEqualByComparingTo("65000");
    }
}

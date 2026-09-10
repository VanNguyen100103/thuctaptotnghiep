package com.ut.edu.backend.order;

import com.ut.edu.backend.payment.Payment;
import com.ut.edu.backend.payment.PaymentMethod;
import com.ut.edu.backend.payment.PaymentRepository;
import com.ut.edu.backend.payment.PaymentStatus;
import com.ut.edu.backend.shipping.goship.GoshipShipmentStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The carrier drives the order now, so these rules run with nobody watching.
 * What matters is what they refuse to do.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderDeliverySyncTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;

    @InjectMocks
    private OrderDeliverySync sync;

    private Order order;

    @BeforeEach
    void setUp() {
        order = Order.builder().id(1L).orderNumber("DH000004").status(OrderStatus.PENDING_COD).build();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
    }

    @Test
    void pickedUpByCourier_putsTheOrderOnTheRoad() {
        // 903 "Bưu tá đã nhận hàng từ shop" - the parcel has left the shop.
        assertThat(sync.applyCarrierStatus(order, 903)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void skippedIntermediateCodes_stillMoveTheOrder() {
        // Webhooks get dropped and retried, so 901 and 902 may never arrive.
        // A per-edge transition table would reject this exact update, which is
        // why this does not go through OrderStatusValidator.
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_COD);
        assertThat(sync.applyCarrierStatus(order, 904)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void deliveredSettlesTheCodPayment() {
        Payment cod = Payment.builder()
                .id(7L).paymentMethod(PaymentMethod.CASH_ON_DELIVERY)
                .status(PaymentStatus.PENDING).amount(new BigDecimal("65000")).build();
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(cod));

        assertThat(sync.applyCarrierStatus(order, 905)).isTrue();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(cod.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepository).save(cod);
    }

    @Test
    void aLateWebhookCannotDragADeliveredOrderBackOntoTheRoad() {
        order.setStatus(OrderStatus.DELIVERED);

        assertThat(sync.applyCarrierStatus(order, 904)).isFalse();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void aCancelledOrderIsNotReopenedByWhateverTheParcelDoesNext() {
        order.setStatus(OrderStatus.CANCELLED);

        assertThat(sync.applyCarrierStatus(order, 905)).isFalse();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void aFailedDeliveryAttemptChangesNothing_becauseTheCourierWillTryAgain() {
        order.setStatus(OrderStatus.SHIPPED);

        // 906 "Bưu tá không giao được hàng" is an attempt, not an outcome.
        assertThat(sync.applyCarrierStatus(order, 906)).isFalse();
        // 907 is on its way back but not back yet.
        assertThat(sync.applyCarrierStatus(order, 907)).isFalse();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void aReturnedParcelEndsTheOrderEvenThoughItWasAlreadyOut() {
        order.setStatus(OrderStatus.SHIPPED);

        // 908 "Chuyển hoàn" - back with the shop, the customer never got it.
        assertThat(sync.applyCarrierStatus(order, 908)).isTrue();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void reconciliationCodesLeaveTheOrderAlone() {
        order.setStatus(OrderStatus.DELIVERED);

        // 909-912 move money between Goship, the carrier and the shop long
        // after the customer has the goods.
        for (int code : new int[] {909, 910, 911, 912}) {
            assertThat(sync.applyCarrierStatus(order, code)).as("code %d", code).isFalse();
        }
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void anUnknownCodeIsIgnoredRatherThanGuessedAt() {
        assertThat(sync.applyCarrierStatus(order, 999)).isFalse();
        assertThat(sync.applyCarrierStatus(order, null)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_COD);
    }

    @Test
    void everyDocumentedCodeIsInTheTable() {
        // Goship documents 900-919 plus 1000. A code missing here would fail
        // silently at runtime - the shipment would update and the order would
        // quietly stop tracking it.
        for (int code = 900; code <= 919; code++) {
            assertThat(GoshipShipmentStatus.of(code)).as("Goship code %d", code).isNotNull();
        }
        assertThat(GoshipShipmentStatus.of(1000)).isNotNull();
    }
}

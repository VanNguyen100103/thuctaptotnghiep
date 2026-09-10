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
import java.util.List;
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
        // 903 "Bưu tá đã nhận hàng từ shop" - the parcel has left the shop, and
        // the order now says exactly that rather than the coarser "đang giao".
        assertThat(sync.applyCarrierStatus(order, 903)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PICKED_UP);
    }

    @Test
    void eachCarrierCodeKeepsItsOwnMeaning() {
        // The four that used to collapse into SHIPPED. A shop chasing a parcel
        // asks which of these it is, so the order has to be able to say.
        record Case(int code, OrderStatus expected) {}
        for (Case c : List.of(
                new Case(903, OrderStatus.PICKED_UP),
                new Case(904, OrderStatus.SHIPPED),
                new Case(918, OrderStatus.AT_WAREHOUSE),
                new Case(919, OrderStatus.IN_TRANSIT))) {
            Order fresh = Order.builder().id(1L).orderNumber("DH1").status(OrderStatus.PROCESSING).build();
            assertThat(sync.applyCarrierStatus(fresh, c.code())).as("code %d", c.code()).isTrue();
            assertThat(fresh.getStatus()).as("code %d", c.code()).isEqualTo(c.expected());
        }
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
    void aRetryAfterAFailedAttemptIsAllowedToGoBackwards() {
        // 906 then 904 again: the courier failed once and is trying a second
        // time. This is why the rule is no longer forward-only - insisting on
        // forward here would freeze the order on the failed attempt.
        order.setStatus(OrderStatus.SHIPPED);

        assertThat(sync.applyCarrierStatus(order, 906)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERY_FAILED);

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
    void aFailedAttemptIsShownWithoutEndingTheOrder() {
        order.setStatus(OrderStatus.SHIPPED);

        // 906 is an attempt, not an outcome, and 907 is on its way back but not
        // back yet. Both are worth showing; neither is terminal, so 908 can
        // still settle it afterwards.
        assertThat(sync.applyCarrierStatus(order, 906)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERY_FAILED);

        assertThat(sync.applyCarrierStatus(order, 907)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNING);

        assertThat(sync.applyCarrierStatus(order, 908)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);
    }

    @Test
    void aReturnedParcelEndsTheOrderEvenThoughItWasAlreadyOut() {
        order.setStatus(OrderStatus.SHIPPED);

        // 908 "Chuyển hoàn" - back with the shop, the customer never got it.
        assertThat(sync.applyCarrierStatus(order, 908)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);

        // And nothing the carrier says afterwards reopens it.
        assertThat(sync.applyCarrierStatus(order, 904)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);
    }

    @Test
    void reconciliationCodesLeaveTheOrderAlone() {
        order.setStatus(OrderStatus.DELIVERED);

        // 909-911 move money between Goship and the carrier long after the
        // customer has the goods, and 915 is a delay flag on wherever the
        // parcel already was. None of them says where it is.
        for (int code : new int[] {909, 910, 911, 915}) {
            assertThat(sync.applyCarrierStatus(order, code)).as("code %d", code).isFalse();
        }
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void theTailAfterDeliveryOnlyRunsForwards() {
        order.setStatus(OrderStatus.DELIVERED);

        // 912 is Goship owing the shop the COD it collected.
        assertThat(sync.applyCarrierStatus(order, 912)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COD_SETTLEMENT);

        // A late webhook cannot put a handed-over parcel back on the road.
        assertThat(sync.applyCarrierStatus(order, 905)).isFalse();
        assertThat(sync.applyCarrierStatus(order, 904)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COD_SETTLEMENT);

        assertThat(sync.applyCarrierStatus(order, 913)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void anUnknownCodeIsIgnoredRatherThanGuessedAt() {
        assertThat(sync.applyCarrierStatus(order, 999)).isFalse();
        assertThat(sync.applyCarrierStatus(order, null)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_COD);
    }

    @Test
    void theIdEntryPointLoadsTheOrderItself() {
        // What the scheduled sweep uses. It hands over an id precisely because
        // it has no session to load an entity through: the shipment it holds
        // carries its order as an uninitialised proxy, and touching that from
        // a job threw LazyInitializationException, which the sweep's own catch
        // swallowed as a warning. It ran every ten minutes and moved nothing.
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThat(sync.applyShipmentUpdate(1L, 903, "GAPBLXAE")).isTrue();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PICKED_UP);
        assertThat(order.getTrackingNumber()).isEqualTo("GAPBLXAE");
    }

    @Test
    void anOrderThatNoLongerExistsIsNotFatalToTheSweep() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(sync.applyShipmentUpdate(99L, 903, null)).isFalse();
        assertThat(sync.applyShipmentUpdate(null, 903, null)).isFalse();
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

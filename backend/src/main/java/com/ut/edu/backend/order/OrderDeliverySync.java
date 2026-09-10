package com.ut.edu.backend.order;

import com.ut.edu.backend.payment.PaymentMethod;
import com.ut.edu.backend.payment.PaymentRepository;
import com.ut.edu.backend.payment.PaymentStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Moves an order along as its parcel moves, from whatever the carrier last
 * said about it.
 *
 * Deliberately not routed through OrderStatusValidator. That table governs
 * what a person may ask for - which transitions the shop is allowed to choose
 * - and refusing an illegal one is the right answer to a person. A carrier
 * saying where the parcel is now is not a request; it is an observation, and
 * the shop cannot argue with it. Webhooks are also dropped and retried, so the
 * intermediate codes are routinely missed: an order can legitimately go
 * straight from PENDING_COD to SHIPPED because 901 and 902 never arrived. A
 * per-edge table would reject exactly the updates that matter most.
 *
 * What replaces it is two rules that cannot corrupt an order:
 *   - only ever forwards along the fulfilment line, so a late webhook arriving
 *     out of order cannot drag a delivered order back to "đang giao";
 *   - never over an ending somebody already decided - a cancelled or refunded
 *     order stays that way, whatever the parcel does afterwards.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderDeliverySync {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    /**
     * How far along fulfilment each status sits. Only these are ranked: the
     * endings are handled separately, because a parcel that comes back or gets
     * lost has to be able to end an order that was already out for delivery.
     */
    private static final Map<OrderStatus, Integer> PROGRESS = Map.of(
            OrderStatus.PENDING, 1,
            OrderStatus.PAYMENT_PENDING, 1,
            OrderStatus.PAID, 2,
            OrderStatus.PENDING_COD, 2,
            OrderStatus.PROCESSING, 3,
            OrderStatus.SHIPPED, 4,
            OrderStatus.DELIVERED, 5);

    /** An outcome nobody should overwrite: the money and the goods have both been settled. */
    private static boolean isSettled(OrderStatus status) {
        return status == OrderStatus.CANCELLED
                || status == OrderStatus.REFUNDED
                || status == OrderStatus.FAILED
                || status == OrderStatus.DELIVERED;
    }

    /**
     * Applies what the carrier's latest status code implies for this order.
     * Returns true when the order actually moved.
     */
    @Transactional
    public boolean applyCarrierStatus(Order order, Integer goshipStatusCode) {
        if (order == null) {
            return false;
        }
        OrderStatus target = com.ut.edu.backend.shipping.goship.GoshipShipmentStatus
                .orderStatusFor(goshipStatusCode);
        if (target == null) {
            // Either a code that says nothing about the order (a retryable
            // delivery attempt, a reconciliation step) or one Goship has added
            // since. The shipment still records it either way.
            return false;
        }

        OrderStatus current = order.getStatus();
        if (current == target) {
            return false;
        }
        if (isSettled(current)) {
            log.debug("Order {} is already {}; ignoring carrier status {}", order.getOrderNumber(), current, goshipStatusCode);
            return false;
        }

        boolean isEnding = target == OrderStatus.FAILED || target == OrderStatus.CANCELLED;
        if (!isEnding) {
            Integer from = PROGRESS.get(current);
            Integer to = PROGRESS.get(target);
            if (from != null && to != null && to <= from) {
                // A webhook that arrived late, or was retried after a newer one
                // already landed. Going backwards here would show a delivered
                // parcel as still on the road.
                log.debug("Ignoring carrier status {} for order {}: {} is not ahead of {}",
                        goshipStatusCode, order.getOrderNumber(), target, current);
                return false;
            }
        }

        order.setStatus(target);
        if (target == OrderStatus.DELIVERED) {
            settleCodOnDelivery(order);
        }
        orderRepository.save(order);
        log.info("Order {} moved {} -> {} by carrier status {}", order.getOrderNumber(), current, target, goshipStatusCode);
        return true;
    }

    /**
     * COD: the courier handing the parcel over IS the moment the cash is
     * collected - there is no gateway callback ever coming for COD (see
     * CodPaymentProvider). Scoped tightly and idempotent: only a
     * CASH_ON_DELIVERY payment still sitting at PENDING, so retries are inert
     * and PayPal/MoMo, already COMPLETED long before delivery, are untouched.
     */
    public void settleCodOnDelivery(Order order) {
        paymentRepository.findByOrderId(order.getId())
                .filter(p -> p.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY)
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .ifPresent(p -> {
                    p.markAsPaid();
                    paymentRepository.save(p);
                    log.info("COD payment {} marked COMPLETED on delivery of order {}", p.getId(), order.getId());
                });
    }
}

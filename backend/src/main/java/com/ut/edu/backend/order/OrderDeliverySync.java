package com.ut.edu.backend.order;

import com.ut.edu.backend.payment.PaymentMethod;
import com.ut.edu.backend.payment.PaymentRepository;
import com.ut.edu.backend.payment.PaymentStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

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
 * Nor is it forward-only any more. Now that an order wears the carrier's own
 * status, a report that moves it backwards is usually a real thing happening:
 * a failed attempt goes back to "đang giao" when the courier tries again. So
 * the report is taken at face value, with two exceptions that cannot be real -
 * an ending somebody already decided (cancelled, refunded) or one the carrier
 * already reached, and a delivered parcel climbing back onto the road.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderDeliverySync {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Statuses no carrier report may change.
     *
     * Two kinds. CANCELLED and REFUNDED are decisions the shop made, and a
     * parcel wandering on afterwards does not undo them. The rest are the
     * carrier's own endings: once a parcel is completed, returned, lost or
     * errored, anything that arrives later is a stale webhook being retried.
     */
    private static final Set<OrderStatus> LOCKED = Set.of(
            OrderStatus.CANCELLED,
            OrderStatus.REFUNDED,
            OrderStatus.COMPLETED,
            OrderStatus.RETURNED,
            OrderStatus.LOST,
            OrderStatus.FAILED);

    /**
     * The tail after a successful delivery, in the order Goship walks it:
     * delivered, then the COD owed to the shop, then done.
     *
     * The only band where direction is still enforced. Everywhere earlier, a
     * carrier moving an order backwards is a real thing happening - a failed
     * attempt returns to "đang giao" when the courier tries again - so the
     * report is taken at face value. Here it never is: a parcel that has been
     * handed over does not go back on the road, so a message saying it did is
     * an old one arriving late.
     */
    private static final List<OrderStatus> AFTER_DELIVERY = List.of(
            OrderStatus.DELIVERED, OrderStatus.COD_SETTLEMENT, OrderStatus.COMPLETED);

    /**
     * Everything a shipment update does to its order: the tracking code the
     * list filters on, and the status the carrier just reported.
     *
     * Takes an id rather than the entity, and that is the point. A shipment
     * loaded outside a request - by the scheduled sweep - carries its order as
     * an uninitialised proxy, and spring.jpa.open-in-view keeps a session open
     * for web requests only. Touching that proxy from the job threw
     * LazyInitializationException, which the sweep's own per-parcel catch then
     * swallowed as a warning: it ran every ten minutes and moved nothing.
     *
     * Reading the id off a proxy does not load it, so the caller can hand that
     * over cheaply and this opens the session it actually needs.
     */
    @Transactional
    public boolean applyShipmentUpdate(Long orderId, Integer goshipStatusCode, String trackingNumber) {
        if (orderId == null) {
            return false;
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Shipment update refers to order {}, which no longer exists", orderId);
            return false;
        }
        // Booking is asynchronous at Goship's end, so the carrier's own code
        // usually arrives on an update rather than in the create response.
        if (trackingNumber != null && !trackingNumber.isBlank()
                && !trackingNumber.equals(order.getTrackingNumber())) {
            order.setTrackingNumber(trackingNumber);
            orderRepository.save(order);
        }
        return applyCarrierStatus(order, goshipStatusCode);
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
        if (LOCKED.contains(current)) {
            log.debug("Order {} is already {}; ignoring carrier status {}", order.getOrderNumber(), current, goshipStatusCode);
            return false;
        }
        int from = AFTER_DELIVERY.indexOf(current);
        if (from >= 0 && AFTER_DELIVERY.indexOf(target) < from) {
            log.debug("Ignoring carrier status {} for order {}: {} is behind {}",
                    goshipStatusCode, order.getOrderNumber(), target, current);
            return false;
        }

        order.setStatus(target);
        if (AFTER_DELIVERY.contains(target)) {
            // Any of the three means the customer has the goods, so the COD is
            // in hand - Goship simply has not paid it over yet at 912.
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

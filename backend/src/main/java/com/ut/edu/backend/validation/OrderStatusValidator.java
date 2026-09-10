package com.ut.edu.backend.validation;

import com.ut.edu.backend.order.Order;

import com.ut.edu.backend.order.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Order Status Validator
 * Ensures safe and valid order status transitions
 * Prevents invalid state changes that could compromise business logic
 */
@Component
@Slf4j
public class OrderStatusValidator {

    // Define valid status transitions (state machine)
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = new HashMap<>();

    static {
        // Before there is a parcel, the shop is still in charge of the order.
        VALID_TRANSITIONS.put(OrderStatus.PENDING, Set.of(
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.PENDING_COD,
                OrderStatus.PAID,
                OrderStatus.CANCELLED,
                OrderStatus.FAILED));

        VALID_TRANSITIONS.put(OrderStatus.PAYMENT_PENDING, Set.of(
                OrderStatus.PAID,
                OrderStatus.FAILED,
                OrderStatus.CANCELLED));

        // PENDING_COD is COD's equivalent of PAID: fulfillment-committed,
        // stock already decremented; only cash collection is still pending,
        // tracked on Payment.status, not here.
        VALID_TRANSITIONS.put(OrderStatus.PENDING_COD, Set.of(
                OrderStatus.PROCESSING,
                OrderStatus.CANCELLED,
                OrderStatus.REFUNDED));

        VALID_TRANSITIONS.put(OrderStatus.PAID, Set.of(
                OrderStatus.PROCESSING,
                OrderStatus.CANCELLED,
                OrderStatus.REFUNDED));

        VALID_TRANSITIONS.put(OrderStatus.PROCESSING, Set.of(
                OrderStatus.SHIPPED,
                OrderStatus.CANCELLED,
                OrderStatus.REFUNDED));

        /*
         * Once a carrier has the parcel, this table stops being the interesting
         * one. It governs what a person may ask for by hand, and a person at
         * the shop cannot decide that a box is in a warehouse - only report
         * that it reached one, which is what OrderDeliverySync does without
         * consulting this at all.
         *
         * So what is left here for the carrier-driven statuses is the small set
         * of decisions a shop genuinely still makes: call the order off, or
         * refund it. Marking one delivered by hand stays available for the shop
         * that delivered it on its own legs and has no carrier to hear from.
         */
        Set<OrderStatus> shopStillDecides = Set.of(
                OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.REFUNDED);
        for (OrderStatus carrierDriven : List.of(
                OrderStatus.AWAITING_PICKUP, OrderStatus.PICKING, OrderStatus.PICKED_UP,
                OrderStatus.AT_WAREHOUSE, OrderStatus.IN_TRANSIT, OrderStatus.SHIPPED,
                OrderStatus.DELIVERY_FAILED, OrderStatus.PARTIALLY_DELIVERED, OrderStatus.RETURNING)) {
            VALID_TRANSITIONS.put(carrierDriven, shopStillDecides);
        }

        // Delivered: the money may still come back, and the carrier's own tail
        // (COD settlement, completion) is not something a person picks.
        VALID_TRANSITIONS.put(OrderStatus.DELIVERED, Set.of(OrderStatus.REFUNDED));
        VALID_TRANSITIONS.put(OrderStatus.COD_SETTLEMENT, Set.of(OrderStatus.REFUNDED));
        VALID_TRANSITIONS.put(OrderStatus.COMPLETED, Set.of(OrderStatus.REFUNDED));

        // A parcel that came back or was lost still owes the customer their
        // money if they had paid.
        VALID_TRANSITIONS.put(OrderStatus.RETURNED, Set.of(OrderStatus.REFUNDED));
        VALID_TRANSITIONS.put(OrderStatus.LOST, Set.of(OrderStatus.REFUNDED));

        // Nothing follows these.
        VALID_TRANSITIONS.put(OrderStatus.CANCELLED, Collections.emptySet());
        VALID_TRANSITIONS.put(OrderStatus.REFUNDED, Collections.emptySet());
        VALID_TRANSITIONS.put(OrderStatus.FAILED, Collections.emptySet());
    }

    /**
     * Validate if status transition is allowed
     *
     * @param currentStatus Current order status
     * @param newStatus New status to transition to
     * @return true if transition is valid, false otherwise
     */
    public boolean isValidTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        // Allow staying in same status (no-op)
        if (currentStatus == newStatus) {
            return true;
        }

        Set<OrderStatus> allowedTransitions = VALID_TRANSITIONS.get(currentStatus);
        if (allowedTransitions == null) {
            log.warn("No transition rules defined for status: {}", currentStatus);
            return false;
        }

        boolean isValid = allowedTransitions.contains(newStatus);

        if (!isValid) {
            log.warn("Invalid status transition attempted: {} -> {}", currentStatus, newStatus);
        }

        return isValid;
    }

    /**
     * Validate status transition and throw exception if invalid
     *
     * @param currentStatus Current order status
     * @param newStatus New status to transition to
     * @throws IllegalStateException if transition is invalid
     */
    public void requireValidTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        if (!isValidTransition(currentStatus, newStatus)) {
            throw new IllegalStateException(
                    String.format("Invalid status transition: Cannot change from %s to %s. " +
                                "Allowed transitions from %s: %s",
                            currentStatus, newStatus, currentStatus,
                            VALID_TRANSITIONS.get(currentStatus))
            );
        }
    }

    /**
     * Get allowed transitions for a status
     *
     * @param currentStatus Current order status
     * @return Set of allowed next statuses
     */
    public Set<OrderStatus> getAllowedTransitions(OrderStatus currentStatus) {
        return VALID_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
    }

    /**
     * Check if status is terminal (no further transitions allowed)
     *
     * @param status Order status to check
     * @return true if status is terminal
     */
    public boolean isTerminalStatus(OrderStatus status) {
        return VALID_TRANSITIONS.get(status).isEmpty();
    }

    /**
     * Validate status string and parse to enum safely
     * SECURITY: Does NOT expose all enum values to prevent information leakage
     *
     * @param statusStr Status string from request
     * @return OrderStatus enum
     * @throws IllegalArgumentException if status is invalid
     */
    public OrderStatus parseAndValidateStatus(String statusStr) {
        if (statusStr == null || statusStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }

        try {
            return OrderStatus.valueOf(statusStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid order status attempted: {}", statusStr);
            // SECURITY: Generic error message - do not expose all enum values
            throw new IllegalArgumentException("Invalid order status provided");
        }
    }

    /**
     * Validate status string and parse to enum with detailed error messages
     * USE ONLY for authenticated ADMIN users - exposes all valid enum values
     *
     * @param statusStr Status string from request
     * @return OrderStatus enum
     * @throws IllegalArgumentException if status is invalid
     */
    public OrderStatus parseAndValidateStatusForAdmin(String statusStr) {
        if (statusStr == null || statusStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }

        try {
            return OrderStatus.valueOf(statusStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid order status attempted by admin: {}", statusStr);
            // ADMIN ONLY: Provide detailed feedback including all valid values
            throw new IllegalArgumentException(
                    "Invalid order status: '" + statusStr + "'. " +
                    "Valid statuses: " + Arrays.toString(OrderStatus.values())
            );
        }
    }

    /**
     * Check if admin can manually set this status
     * Some statuses should only be set by system, not manually by admin
     *
     * @param status Status to check
     * @return true if admin can manually set this status
     */
    public boolean canAdminSetStatus(OrderStatus status) {
        // Admins should not manually set these statuses (system-driven):
        // FAILED by the payment system, PENDING_COD by CodPaymentProvider's
        // synchronous confirmation at checkout time.
        return status != OrderStatus.FAILED && status != OrderStatus.PENDING_COD;
    }

    /**
     * Get transition validation message
     *
     * @param currentStatus Current status
     * @param newStatus Desired new status
     * @return Validation message or null if valid
     */
    public String getTransitionValidationMessage(OrderStatus currentStatus, OrderStatus newStatus) {
        if (currentStatus == newStatus) {
            return null; // Same status, no change
        }

        if (isTerminalStatus(currentStatus)) {
            return String.format("Cannot change status from %s - this is a terminal state", currentStatus);
        }

        if (!canAdminSetStatus(newStatus)) {
            return String.format("Status %s cannot be set manually by admin", newStatus);
        }

        if (!isValidTransition(currentStatus, newStatus)) {
            Set<OrderStatus> allowed = getAllowedTransitions(currentStatus);
            return String.format("Cannot transition from %s to %s. Allowed: %s",
                    currentStatus, newStatus, allowed);
        }

        return null; // Valid transition
    }
}

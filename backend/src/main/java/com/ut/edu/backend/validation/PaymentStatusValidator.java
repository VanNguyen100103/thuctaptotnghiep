package com.ut.edu.backend.validation;

import com.ut.edu.backend.enums.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Payment Status Validator
 * Ensures safe payment status handling and prevents information leakage
 * Provides role-based error messages
 */
@Component
@Slf4j
public class PaymentStatusValidator {

    // Define valid payment status transitions (state machine)
    private static final Map<PaymentStatus, Set<PaymentStatus>> VALID_TRANSITIONS = new HashMap<>();

    static {
        // PENDING can go to: PROCESSING, COMPLETED, FAILED, CANCELLED
        VALID_TRANSITIONS.put(PaymentStatus.PENDING, Set.of(
                PaymentStatus.PROCESSING,
                PaymentStatus.COMPLETED,
                PaymentStatus.FAILED,
                PaymentStatus.CANCELLED
        ));

        // PROCESSING can go to: COMPLETED, FAILED
        VALID_TRANSITIONS.put(PaymentStatus.PROCESSING, Set.of(
                PaymentStatus.COMPLETED,
                PaymentStatus.FAILED
        ));

        // COMPLETED can go to: REFUNDED, PARTIALLY_REFUNDED
        VALID_TRANSITIONS.put(PaymentStatus.COMPLETED, Set.of(
                PaymentStatus.REFUNDED,
                PaymentStatus.PARTIALLY_REFUNDED
        ));

        // PARTIALLY_REFUNDED can go to: REFUNDED
        VALID_TRANSITIONS.put(PaymentStatus.PARTIALLY_REFUNDED, Set.of(
                PaymentStatus.REFUNDED
        ));

        // FAILED, CANCELLED, REFUNDED are terminal states - no transitions
        VALID_TRANSITIONS.put(PaymentStatus.FAILED, Collections.emptySet());
        VALID_TRANSITIONS.put(PaymentStatus.CANCELLED, Collections.emptySet());
        VALID_TRANSITIONS.put(PaymentStatus.REFUNDED, Collections.emptySet());
    }

    /**
     * Validate if payment status transition is allowed
     *
     * @param currentStatus Current payment status
     * @param newStatus New status to transition to
     * @return true if transition is valid, false otherwise
     */
    public boolean isValidTransition(PaymentStatus currentStatus, PaymentStatus newStatus) {
        if (currentStatus == newStatus) {
            return true;
        }

        Set<PaymentStatus> allowedTransitions = VALID_TRANSITIONS.get(currentStatus);
        if (allowedTransitions == null) {
            log.warn("No transition rules defined for payment status: {}", currentStatus);
            return false;
        }

        boolean isValid = allowedTransitions.contains(newStatus);

        if (!isValid) {
            log.warn("Invalid payment status transition attempted: {} -> {}", currentStatus, newStatus);
        }

        return isValid;
    }

    /**
     * Get allowed transitions for a payment status
     *
     * @param currentStatus Current payment status
     * @return Set of allowed next statuses
     */
    public Set<PaymentStatus> getAllowedTransitions(PaymentStatus currentStatus) {
        return VALID_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
    }

    /**
     * Check if payment status is terminal (no further transitions allowed)
     *
     * @param status Payment status to check
     * @return true if status is terminal
     */
    public boolean isTerminalStatus(PaymentStatus status) {
        return VALID_TRANSITIONS.get(status).isEmpty();
    }

    /**
     * Validate status string and parse to enum safely
     * SECURITY: Does NOT expose all enum values to prevent information leakage
     * Use this for PUBLIC/NON-ADMIN endpoints
     *
     * @param statusStr Status string from request
     * @return PaymentStatus enum
     * @throws IllegalArgumentException if status is invalid
     */
    public PaymentStatus parseAndValidateStatus(String statusStr) {
        if (statusStr == null || statusStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment status cannot be null or empty");
        }

        try {
            return PaymentStatus.valueOf(statusStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid payment status attempted: {}", statusStr);
            // SECURITY: Generic error message - do not expose all enum values
            throw new IllegalArgumentException("Invalid payment status provided");
        }
    }

    /**
     * Validate status string and parse to enum with detailed error messages
     * USE ONLY for authenticated ADMIN users - exposes all valid enum values
     *
     * @param statusStr Status string from request
     * @return PaymentStatus enum
     * @throws IllegalArgumentException if status is invalid
     */
    public PaymentStatus parseAndValidateStatusForAdmin(String statusStr) {
        if (statusStr == null || statusStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment status cannot be null or empty");
        }

        try {
            return PaymentStatus.valueOf(statusStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid payment status attempted by admin: {}", statusStr);
            // ADMIN ONLY: Provide detailed feedback including all valid values
            throw new IllegalArgumentException(
                    "Invalid payment status: '" + statusStr + "'. " +
                    "Valid statuses: " + Arrays.toString(PaymentStatus.values())
            );
        }
    }

    /**
     * Check if admin can manually set this payment status
     * Some statuses should only be set by payment gateway, not manually by admin
     *
     * @param status Status to check
     * @return true if admin can manually set this status
     */
    public boolean canAdminSetStatus(PaymentStatus status) {
        // Admins should not manually set these statuses (system/gateway-driven)
        return status != PaymentStatus.PROCESSING &&
               status != PaymentStatus.COMPLETED;  // These come from PayPal
    }

    /**
     * Get transition validation message
     *
     * @param currentStatus Current status
     * @param newStatus Desired new status
     * @return Validation message or null if valid
     */
    public String getTransitionValidationMessage(PaymentStatus currentStatus, PaymentStatus newStatus) {
        if (currentStatus == newStatus) {
            return null;
        }

        if (isTerminalStatus(currentStatus)) {
            return String.format("Cannot change status from %s - this is a terminal state", currentStatus);
        }

        if (!canAdminSetStatus(newStatus)) {
            return String.format("Status %s cannot be set manually by admin - it's set by payment gateway", newStatus);
        }

        if (!isValidTransition(currentStatus, newStatus)) {
            Set<PaymentStatus> allowed = getAllowedTransitions(currentStatus);
            return String.format("Cannot transition from %s to %s. Allowed: %s",
                    currentStatus, newStatus, allowed);
        }

        return null;
    }

    /**
     * Validate payment status transition and throw exception if invalid
     *
     * @param currentStatus Current payment status
     * @param newStatus New status to transition to
     * @throws IllegalStateException if transition is invalid
     */
    public void requireValidTransition(PaymentStatus currentStatus, PaymentStatus newStatus) {
        String validationMessage = getTransitionValidationMessage(currentStatus, newStatus);
        if (validationMessage != null) {
            throw new IllegalStateException(validationMessage);
        }
    }
}

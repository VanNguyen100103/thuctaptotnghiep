package com.ut.edu.backend.validation;

import com.ut.edu.backend.payment.Payment;

import com.ut.edu.backend.payment.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Payment Method Validator
 * Ensures safe payment method handling and prevents information leakage
 * Provides role-based error messages
 */
@Component
@Slf4j
public class PaymentMethodValidator {

    // Define allowed payment methods (can be configured based on business rules)
    private static final Set<PaymentMethod> ALLOWED_METHODS = Set.of(
            PaymentMethod.PAYPAL,
            PaymentMethod.CREDIT_CARD,
            PaymentMethod.DEBIT_CARD,
            PaymentMethod.BANK_TRANSFER,
            PaymentMethod.CASH_ON_DELIVERY
    );

    // Define payment methods available for online payment
    private static final Set<PaymentMethod> ONLINE_PAYMENT_METHODS = Set.of(
            PaymentMethod.PAYPAL,
            PaymentMethod.CREDIT_CARD,
            PaymentMethod.DEBIT_CARD,
            PaymentMethod.BANK_TRANSFER
    );

    // Define payment methods that require gateway integration
    private static final Set<PaymentMethod> GATEWAY_REQUIRED_METHODS = Set.of(
            PaymentMethod.PAYPAL,
            PaymentMethod.CREDIT_CARD,
            PaymentMethod.DEBIT_CARD
    );

    /**
     * Check if payment method is allowed
     *
     * @param method Payment method to check
     * @return true if method is allowed
     */
    public boolean isAllowedMethod(PaymentMethod method) {
        return ALLOWED_METHODS.contains(method);
    }

    /**
     * Check if payment method is online payment
     *
     * @param method Payment method to check
     * @return true if method is online payment
     */
    public boolean isOnlinePayment(PaymentMethod method) {
        return ONLINE_PAYMENT_METHODS.contains(method);
    }

    /**
     * Check if payment method requires gateway integration
     *
     * @param method Payment method to check
     * @return true if method requires gateway
     */
    public boolean requiresGateway(PaymentMethod method) {
        return GATEWAY_REQUIRED_METHODS.contains(method);
    }

    /**
     * Get all allowed payment methods
     *
     * @return Set of allowed payment methods
     */
    public Set<PaymentMethod> getAllowedMethods() {
        return ALLOWED_METHODS;
    }

    /**
     * Validate payment method string and parse to enum safely
     * SECURITY: Does NOT expose all enum values to prevent information leakage
     * Use this for PUBLIC/NON-ADMIN endpoints
     *
     * @param methodStr Payment method string from request
     * @return PaymentMethod enum
     * @throws IllegalArgumentException if method is invalid
     */
    public PaymentMethod parseAndValidateMethod(String methodStr) {
        if (methodStr == null || methodStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment method cannot be null or empty");
        }

        try {
            PaymentMethod method = PaymentMethod.valueOf(methodStr.toUpperCase().trim());

            if (!isAllowedMethod(method)) {
                log.warn("Disallowed payment method attempted: {}", methodStr);
                throw new IllegalArgumentException("Payment method not available");
            }

            return method;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid payment method attempted: {}", methodStr);
            // SECURITY: Generic error message - do not expose all enum values
            throw new IllegalArgumentException("Invalid payment method provided");
        }
    }

    /**
     * Validate payment method string and parse to enum with detailed error messages
     * USE ONLY for authenticated ADMIN users - exposes all valid enum values
     *
     * @param methodStr Payment method string from request
     * @return PaymentMethod enum
     * @throws IllegalArgumentException if method is invalid
     */
    public PaymentMethod parseAndValidateMethodForAdmin(String methodStr) {
        if (methodStr == null || methodStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment method cannot be null or empty");
        }

        try {
            PaymentMethod method = PaymentMethod.valueOf(methodStr.toUpperCase().trim());

            if (!isAllowedMethod(method)) {
                log.warn("Disallowed payment method attempted by admin: {}", methodStr);
                // ADMIN ONLY: Detailed feedback
                throw new IllegalArgumentException(
                        "Payment method '" + methodStr + "' is not currently allowed. " +
                        "Allowed methods: " + ALLOWED_METHODS
                );
            }

            return method;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid payment method attempted by admin: {}", methodStr);
            // ADMIN ONLY: Provide detailed feedback including all valid values
            throw new IllegalArgumentException(
                    "Invalid payment method: '" + methodStr + "'. " +
                    "Valid methods: " + Arrays.toString(PaymentMethod.values())
            );
        }
    }

    /**
     * Validate that payment method is allowed for current order
     *
     * @param method Payment method
     * @param requireOnline Whether online payment is required
     * @throws IllegalArgumentException if method is not allowed
     */
    public void validateMethodForOrder(PaymentMethod method, boolean requireOnline) {
        if (!isAllowedMethod(method)) {
            throw new IllegalArgumentException("Payment method not available");
        }

        if (requireOnline && !isOnlinePayment(method)) {
            throw new IllegalArgumentException("This order requires online payment");
        }
    }

    /**
     * Get validation message for payment method
     *
     * @param method Payment method to validate
     * @param requireOnline Whether online payment is required
     * @return Validation message or null if valid
     */
    public String getValidationMessage(PaymentMethod method, boolean requireOnline) {
        if (!isAllowedMethod(method)) {
            return "Payment method is not currently available";
        }

        if (requireOnline && !isOnlinePayment(method)) {
            return "This order requires online payment";
        }

        return null;
    }
}

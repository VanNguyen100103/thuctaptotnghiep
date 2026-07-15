package com.ut.edu.backend.validation;

import com.ut.edu.backend.user.Address;

import com.ut.edu.backend.user.AddressType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Address Type Validator
 * Ensures safe address type handling and prevents information leakage
 * Provides role-based error messages
 */
@Component
@Slf4j
public class AddressTypeValidator {

    // Define allowed address types
    private static final Set<AddressType> ALLOWED_TYPES = Set.of(
            AddressType.SHIPPING,
            AddressType.BILLING,
            AddressType.BOTH
    );

    /**
     * Check if address type is allowed
     *
     * @param type Address type to check
     * @return true if type is allowed
     */
    public boolean isAllowedType(AddressType type) {
        return ALLOWED_TYPES.contains(type);
    }

    /**
     * Check if address type can be used for shipping
     *
     * @param type Address type to check
     * @return true if type can be used for shipping
     */
    public boolean canBeUsedForShipping(AddressType type) {
        return type == AddressType.SHIPPING || type == AddressType.BOTH;
    }

    /**
     * Check if address type can be used for billing
     *
     * @param type Address type to check
     * @return true if type can be used for billing
     */
    public boolean canBeUsedForBilling(AddressType type) {
        return type == AddressType.BILLING || type == AddressType.BOTH;
    }

    /**
     * Get all allowed address types
     *
     * @return Set of allowed address types
     */
    public Set<AddressType> getAllowedTypes() {
        return ALLOWED_TYPES;
    }

    /**
     * Validate address type string and parse to enum safely
     * SECURITY: Does NOT expose all enum values to prevent information leakage
     * Use this for PUBLIC/NON-ADMIN endpoints
     *
     * @param typeStr Address type string from request
     * @return AddressType enum
     * @throws IllegalArgumentException if type is invalid
     */
    public AddressType parseAndValidateType(String typeStr) {
        if (typeStr == null || typeStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Address type cannot be null or empty");
        }

        try {
            AddressType type = AddressType.valueOf(typeStr.toUpperCase().trim());

            if (!isAllowedType(type)) {
                log.warn("Disallowed address type attempted: {}", typeStr);
                throw new IllegalArgumentException("Address type not available");
            }

            return type;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid address type attempted: {}", typeStr);
            // SECURITY: Generic error message - do not expose all enum values
            throw new IllegalArgumentException("Invalid address type provided");
        }
    }

    /**
     * Validate address type string and parse to enum with detailed error messages
     * USE ONLY for authenticated ADMIN users - exposes all valid enum values
     *
     * @param typeStr Address type string from request
     * @return AddressType enum
     * @throws IllegalArgumentException if type is invalid
     */
    public AddressType parseAndValidateTypeForAdmin(String typeStr) {
        if (typeStr == null || typeStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Address type cannot be null or empty");
        }

        try {
            AddressType type = AddressType.valueOf(typeStr.toUpperCase().trim());

            if (!isAllowedType(type)) {
                log.warn("Disallowed address type attempted by admin: {}", typeStr);
                // ADMIN ONLY: Detailed feedback
                throw new IllegalArgumentException(
                        "Address type '" + typeStr + "' is not currently allowed. " +
                        "Allowed types: " + ALLOWED_TYPES
                );
            }

            return type;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid address type attempted by admin: {}", typeStr);
            // ADMIN ONLY: Provide detailed feedback including all valid values
            throw new IllegalArgumentException(
                    "Invalid address type: '" + typeStr + "'. " +
                    "Valid types: " + Arrays.toString(AddressType.values())
            );
        }
    }

    /**
     * Get default address type
     *
     * @return Default SHIPPING type
     */
    public AddressType getDefaultType() {
        return AddressType.SHIPPING;
    }

    /**
     * Validate that address type is appropriate for the operation
     *
     * @param type Address type
     * @param requireShipping Whether shipping capability is required
     * @param requireBilling Whether billing capability is required
     * @throws IllegalArgumentException if type doesn't meet requirements
     */
    public void validateTypeForOperation(AddressType type, boolean requireShipping, boolean requireBilling) {
        if (!isAllowedType(type)) {
            throw new IllegalArgumentException("Address type not available");
        }

        if (requireShipping && !canBeUsedForShipping(type)) {
            throw new IllegalArgumentException("This address cannot be used for shipping");
        }

        if (requireBilling && !canBeUsedForBilling(type)) {
            throw new IllegalArgumentException("This address cannot be used for billing");
        }
    }

    /**
     * Get validation message for address type
     *
     * @param type Address type to validate
     * @param requireShipping Whether shipping capability is required
     * @param requireBilling Whether billing capability is required
     * @return Validation message or null if valid
     */
    public String getValidationMessage(AddressType type, boolean requireShipping, boolean requireBilling) {
        if (!isAllowedType(type)) {
            return "Address type is not available";
        }

        if (requireShipping && !canBeUsedForShipping(type)) {
            return "This address type cannot be used for shipping";
        }

        if (requireBilling && !canBeUsedForBilling(type)) {
            return "This address type cannot be used for billing";
        }

        return null;
    }
}

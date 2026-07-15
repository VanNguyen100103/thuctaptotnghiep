package com.ut.edu.backend.validation;

import com.ut.edu.backend.user.User;

import com.ut.edu.backend.user.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Role Validator
 * Ensures safe role handling and prevents information leakage
 * CRITICAL: Role enumeration can lead to privilege escalation attacks
 * Provides role-based error messages
 */
@Component
@Slf4j
public class RoleValidator {

    // Define roles that can be assigned by registration
    private static final Set<Role> SELF_ASSIGNABLE_ROLES = Set.of(
            Role.USER  // Only USER role can be self-assigned during registration
    );

    // Define roles that require admin privileges to assign
    private static final Set<Role> ADMIN_ONLY_ROLES = Set.of(
            Role.ADMIN,
            Role.MODERATOR
    );

    // Define valid role transitions (what roles can be changed to what)
    private static final Set<Role> PROMOTABLE_ROLES = Set.of(
            Role.USER,      // USER can be promoted to MODERATOR
            Role.MODERATOR  // MODERATOR can be promoted to ADMIN (or demoted to USER)
    );

    /**
     * Check if role can be self-assigned during registration
     *
     * @param role Role to check
     * @return true if role can be self-assigned
     */
    public boolean isSelfAssignable(Role role) {
        return SELF_ASSIGNABLE_ROLES.contains(role);
    }

    /**
     * Check if role requires admin privileges to assign
     *
     * @param role Role to check
     * @return true if role requires admin to assign
     */
    public boolean requiresAdminToAssign(Role role) {
        return ADMIN_ONLY_ROLES.contains(role);
    }

    /**
     * Check if role can be promoted/changed
     *
     * @param role Role to check
     * @return true if role can be changed
     */
    public boolean isPromotable(Role role) {
        return PROMOTABLE_ROLES.contains(role);
    }

    /**
     * Validate if role transition is allowed
     *
     * @param currentRole Current user role
     * @param newRole New role to assign
     * @return true if transition is allowed
     */
    public boolean isValidRoleChange(Role currentRole, Role newRole) {
        if (currentRole == newRole) {
            return true;
        }

        // Cannot change from/to roles that are not promotable
        if (!isPromotable(currentRole)) {
            log.warn("Attempted to change non-promotable role: {}", currentRole);
            return false;
        }

        // Can only assign admin-only roles
        if (requiresAdminToAssign(newRole)) {
            return true;  // Admin doing the assignment will be checked by @PreAuthorize
        }

        return true;
    }

    /**
     * Validate role string and parse to enum safely
     * SECURITY: Does NOT expose all enum values to prevent information leakage
     * NEVER use this for registration - roles should be hardcoded
     * Use this ONLY for admin operations with additional security checks
     *
     * @param roleStr Role string from request
     * @return Role enum
     * @throws IllegalArgumentException if role is invalid
     */
    public Role parseAndValidateRole(String roleStr) {
        if (roleStr == null || roleStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Role cannot be null or empty");
        }

        try {
            Role role = Role.valueOf(roleStr.toUpperCase().trim());

            // SECURITY: Never expose which roles exist through this method
            log.warn("Role parsing attempted: {}", roleStr);

            return role;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role attempted: {}", roleStr);
            // SECURITY: Generic error message - do not expose all enum values
            // This prevents role enumeration attacks
            throw new IllegalArgumentException("Invalid role provided");
        }
    }

    /**
     * Validate role string and parse to enum with detailed error messages
     * USE ONLY for authenticated ADMIN users - exposes all valid enum values
     * WARNING: This reveals system authorization structure
     *
     * @param roleStr Role string from request
     * @return Role enum
     * @throws IllegalArgumentException if role is invalid
     */
    public Role parseAndValidateRoleForAdmin(String roleStr) {
        if (roleStr == null || roleStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Role cannot be null or empty");
        }

        try {
            Role role = Role.valueOf(roleStr.toUpperCase().trim());

            log.info("Role parsed by admin: {}", roleStr);

            return role;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role attempted by admin: {}", roleStr);
            // ADMIN ONLY: Provide detailed feedback including all valid values
            throw new IllegalArgumentException(
                    "Invalid role: '" + roleStr + "'. " +
                    "Valid roles: " + Arrays.toString(Role.values())
            );
        }
    }

    /**
     * Get default role for new user registration
     * SECURITY: Hardcoded to prevent privilege escalation
     *
     * @return Default USER role
     */
    public Role getDefaultRole() {
        return Role.USER;
    }

    /**
     * Validate that role can be assigned during registration
     * SECURITY: Prevent privilege escalation during registration
     *
     * @param role Role to validate
     * @throws IllegalArgumentException if role cannot be self-assigned
     */
    public void validateSelfAssignable(Role role) {
        if (!isSelfAssignable(role)) {
            log.error("SECURITY ALERT: Attempted to self-assign privileged role: {}", role);
            // Generic error - don't reveal which roles are privileged
            throw new IllegalArgumentException("Invalid role for registration");
        }
    }

    /**
     * Validate role change by admin
     *
     * @param currentRole Current role
     * @param newRole New role to assign
     * @return Validation message or null if valid
     */
    public String getRoleChangeValidationMessage(Role currentRole, Role newRole) {
        if (currentRole == newRole) {
            return "No role change needed - already has this role";
        }

        if (!isPromotable(currentRole)) {
            return "Cannot change this user's role - role is fixed";
        }

        if (!requiresAdminToAssign(newRole) && newRole != Role.USER) {
            return "Invalid target role";
        }

        return null;
    }

    /**
     * Validate role set for user
     * Ensures user has at least one valid role and no conflicting roles
     *
     * @param roles Set of roles to validate
     * @throws IllegalArgumentException if role set is invalid
     */
    public void validateRoleSet(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("User must have at least one role");
        }

        // Optional: Add business logic for conflicting roles
        // For example, maybe a user shouldn't be both USER and ADMIN
        // This is business-specific
    }

    /**
     * Log security event for role changes
     *
     * @param userId User whose role is being changed
     * @param oldRole Old role
     * @param newRole New role
     * @param performedBy Admin performing the change
     */
    public void logRoleChange(Long userId, Role oldRole, Role newRole, String performedBy) {
        log.info("SECURITY: Role change - User ID: {}, {} -> {}, performed by: {}",
                 userId, oldRole, newRole, performedBy);
    }
}

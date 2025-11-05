package com.ut.edu.backend.controller;

import com.ut.edu.backend.enums.Role;
import com.ut.edu.backend.model.User;
import com.ut.edu.backend.repository.UserRepository;
import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.service.inter.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin Controller - User Management
 * Restricted to ADMIN role only
 * Provides full CRUD operations on users
 */
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IUserService userService;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Get all users with pagination and filtering
     * GET /api/admin/users?page=0&size=20&search=john&role=USER&enabled=true
     */
    @GetMapping
    public ResponseEntity<?> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean enabled) {
        try {
            Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

            Pageable pageable = PageRequest.of(page, size, sort);

            // IMPLEMENTED: Filtering by role, enabled status, and search
            Page<User> users;

            // Parse role enum if provided
            Role roleEnum = null;
            if (role != null && !role.trim().isEmpty()) {
                try {
                    roleEnum = Role.valueOf(role.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Invalid role: " + role));
                }
            }

            // Apply filters
            if (search != null && !search.trim().isEmpty()) {
                // Search with filters
                users = userRepository.searchUsersWithFilters(search, roleEnum, enabled, pageable);
            } else if (roleEnum != null || enabled != null) {
                // Filter by role/enabled only
                users = userRepository.findByRoleAndEnabled(roleEnum, enabled, pageable);
            } else {
                // No filters - get all
                users = userRepository.findAll(pageable);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("users", users.getContent());
            response.put("currentPage", users.getNumber());
            response.put("totalItems", users.getTotalElements());
            response.put("totalPages", users.getTotalPages());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve users"));
        }
    }

    /**
     * Get user by ID
     * GET /api/admin/users/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserById(@PathVariable Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            return ResponseEntity.ok(user);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve user"));
        }
    }

    /**
     * Update user details (admin can update any field)
     * PUT /api/admin/users/{userId}
     */
    @PutMapping("/{userId}")
    public ResponseEntity<?> updateUser(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> updates) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            // Update fields if provided
            if (updates.containsKey("firstName")) {
                user.setFirstName((String) updates.get("firstName"));
            }
            if (updates.containsKey("lastName")) {
                user.setLastName((String) updates.get("lastName"));
            }
            if (updates.containsKey("email")) {
                String newEmail = (String) updates.get("email");
                if (!newEmail.equals(user.getEmail()) && userService.existsByEmail(newEmail)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Email already exists"));
                }
                user.setEmail(newEmail);
            }
            if (updates.containsKey("phoneNumber")) {
                user.setPhoneNumber((String) updates.get("phoneNumber"));
            }

            User updatedUser = userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "message", "User updated successfully",
                    "user", updatedUser
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update user"));
        }
    }

    /**
     * Enable/Disable user account
     * PATCH /api/admin/users/{userId}/status
     */
    @PatchMapping("/{userId}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable Long userId,
            @RequestBody Map<String, Boolean> request) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            Boolean enabled = request.get("enabled");
            if (enabled == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "enabled field is required"));
            }

            user.setEnabled(enabled);
            userRepository.save(user);

            String status = enabled ? "enabled" : "disabled";
            log.info("User {} has been {}", userId, status);

            return ResponseEntity.ok(Map.of(
                    "message", "User account " + status + " successfully",
                    "userId", userId,
                    "enabled", enabled
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update user status: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update user status"));
        }
    }

    /**
     * Update user roles
     * PATCH /api/admin/users/{userId}/roles
     */
    @PatchMapping("/{userId}/roles")
    public ResponseEntity<?> updateUserRoles(
            @PathVariable Long userId,
            @RequestBody Map<String, Set<Role>> request) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            Set<Role> roles = request.get("roles");
            if (roles == null || roles.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "At least one role is required"));
            }

            user.setRoles(roles);
            userRepository.save(user);

            log.info("User {} roles updated to: {}", userId, roles);

            return ResponseEntity.ok(Map.of(
                    "message", "User roles updated successfully",
                    "userId", userId,
                    "roles", roles
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update user roles: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update user roles"));
        }
    }

    /**
     * Delete user (soft delete - disable account)
     * DELETE /api/admin/users/{userId}
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        try {
            // Prevent deleting self
            Long currentAdminId = authorizationService.getCurrentUserId();
            if (currentAdminId.equals(userId)) {
                log.warn("Admin {} attempted to delete their own account", currentAdminId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You cannot delete your own admin account"));
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            // Soft delete - disable account
            user.setEnabled(false);
            userRepository.save(user);

            log.warn("User {} deleted (disabled) by admin", userId);

            return ResponseEntity.ok(Map.of(
                    "message", "User deleted successfully",
                    "userId", userId
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to delete user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete user"));
        }
    }

    /**
     * Get user statistics
     * GET /api/admin/users/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getUserStatistics() {
        try {
            long totalUsers = userRepository.count();
            long enabledUsers = userRepository.findAll().stream()
                    .filter(user -> user.getEnabled() != null && user.getEnabled())
                    .count();
            long disabledUsers = totalUsers - enabledUsers;

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalUsers", totalUsers);
            stats.put("enabledUsers", enabledUsers);
            stats.put("disabledUsers", disabledUsers);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Failed to get user statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve statistics"));
        }
    }

    /**
     * Search users by username or email
     * GET /api/admin/users/search?query=john
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            // Search by username or email - filter in memory
            List<User> allUsers = userRepository.findAll();
            List<User> matchedUsers = allUsers.stream()
                    .filter(user ->
                            user.getUsername().toLowerCase().contains(query.toLowerCase()) ||
                            user.getEmail().toLowerCase().contains(query.toLowerCase()))
                    .skip((long) page * size)
                    .limit(size)
                    .collect(java.util.stream.Collectors.toList());

            long totalMatched = allUsers.stream()
                    .filter(user ->
                            user.getUsername().toLowerCase().contains(query.toLowerCase()) ||
                            user.getEmail().toLowerCase().contains(query.toLowerCase()))
                    .count();

            Map<String, Object> response = new HashMap<>();
            response.put("users", matchedUsers);
            response.put("currentPage", page);
            response.put("totalItems", totalMatched);
            response.put("totalPages", (totalMatched + size - 1) / size);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to search users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to search users"));
        }
    }

    /**
     * Create new user (admin action)
     * POST /api/admin/users
     */
    @PostMapping
    public ResponseEntity<?> createUser(@jakarta.validation.Valid @RequestBody CreateUserRequest request) {
        try {
            // Check if username already exists
            if (userRepository.existsByUsername(request.getUsername())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Username already exists"));
            }

            // Check if email already exists
            if (userRepository.existsByEmail(request.getEmail())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email already exists"));
            }

            // Create user directly with encoded password
            User newUser = User.builder()
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .phoneNumber(request.getPhoneNumber())
                    .roles(request.getRoles() != null ? request.getRoles() : Set.of(Role.USER))
                    .enabled(true)
                    .accountNonLocked(true)
                    .failedLoginAttempts(0)
                    .build();

            newUser = userRepository.save(newUser);

            log.info("User created by admin: {} ({})", newUser.getUsername(), newUser.getEmail());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "User created successfully",
                            "user", Map.of(
                                    "id", newUser.getId(),
                                    "username", newUser.getUsername(),
                                    "email", newUser.getEmail(),
                                    "firstName", newUser.getFirstName(),
                                    "lastName", newUser.getLastName(),
                                    "roles", newUser.getRoles()
                            )
                    ));

        } catch (Exception e) {
            log.error("Failed to create user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create user: " + e.getMessage()));
        }
    }

    /**
     * Reset user password (admin action)
     * POST /api/admin/users/{userId}/reset-password
     */
    @PostMapping("/{userId}/reset-password")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> resetUserPassword(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        try {
            // Verify user exists first
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            String newPassword = request.get("newPassword");
            if (newPassword == null || newPassword.length() < 8) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Password must be at least 8 characters"));
            }

            // Encode new password
            String encodedPassword = passwordEncoder.encode(newPassword);

            log.info("Resetting password for user: {} (ID: {})", user.getUsername(), userId);
            log.info("Old password hash: {}", user.getPassword().substring(0, 20) + "...");
            log.info("New password hash: {}", encodedPassword.substring(0, 20) + "...");

            // Update password using native approach to ensure it's saved
            user.setPassword(encodedPassword);
            user.setFailedLoginAttempts(0);
            user.setAccountNonLocked(true);

            // Save with flush to force immediate write
            userRepository.saveAndFlush(user);

            // Re-fetch from database to verify
            User verifiedUser = userRepository.findById(userId).orElseThrow();
            log.info("Verified password hash in DB: {}", verifiedUser.getPassword().substring(0, 20) + "...");

            boolean passwordMatches = verifiedUser.getPassword().equals(encodedPassword);
            log.warn("Password reset by admin for user: {} ({}), password saved correctly: {}",
                    user.getUsername(), userId, passwordMatches);

            return ResponseEntity.ok(Map.of(
                    "message", "Password reset successfully",
                    "userId", userId,
                    "verified", passwordMatches
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to reset password for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reset password: " + e.getMessage()));
        }
    }

    // ==================== REQUEST DTOs ====================

    /**
     * DTO for creating a new user (admin action)
     */
    @lombok.Data
    public static class CreateUserRequest {
        @jakarta.validation.constraints.NotBlank(message = "Username is required")
        @jakarta.validation.constraints.Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        private String username;

        @jakarta.validation.constraints.NotBlank(message = "Email is required")
        @jakarta.validation.constraints.Email(message = "Email should be valid")
        private String email;

        @jakarta.validation.constraints.NotBlank(message = "Password is required")
        @jakarta.validation.constraints.Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        private String password;

        @jakarta.validation.constraints.NotBlank(message = "First name is required")
        private String firstName;

        @jakarta.validation.constraints.NotBlank(message = "Last name is required")
        private String lastName;

        private String phoneNumber;

        private Set<Role> roles;  // Optional, defaults to USER
    }
}

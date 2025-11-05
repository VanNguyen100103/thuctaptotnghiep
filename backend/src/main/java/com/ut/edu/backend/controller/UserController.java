package com.ut.edu.backend.controller;

import com.ut.edu.backend.model.User;
import com.ut.edu.backend.repository.UserRepository;
import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.service.inter.ICloudinaryService;
import com.ut.edu.backend.service.inter.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * User Controller
 * Manages user profiles with IDOR protection
 */
@RestController
@RequestMapping("/users")
@Slf4j
public class UserController {

    @Autowired
    private IUserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private ICloudinaryService cloudinaryService;

    /**
     * Get current user profile
     * GET /api/users/me
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getCurrentUser() {
        try {
            User currentUser = authorizationService.getCurrentUser();

            return ResponseEntity.ok(Map.of(
                    "id", currentUser.getId(),
                    "username", currentUser.getUsername(),
                    "email", currentUser.getEmail(),
                    "firstName", currentUser.getFirstName() != null ? currentUser.getFirstName() : "",
                    "lastName", currentUser.getLastName() != null ? currentUser.getLastName() : "",
                    "phoneNumber", currentUser.getPhoneNumber() != null ? currentUser.getPhoneNumber() : "",
                    "avatarUrl", currentUser.getAvatarUrl() != null ? currentUser.getAvatarUrl() : "",
                    "roles", currentUser.getRoles(),
                    "enabled", currentUser.getEnabled()
            ));

        } catch (Exception e) {
            log.error("Failed to get current user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve user information"));
        }
    }

    /**
     * Get user by ID (with IDOR protection)
     * GET /api/users/{userId}
     * Only accessible by the user themselves or admin
     */
    @GetMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getUserById(@PathVariable Long userId) {
        try {
            // IDOR Protection: Check if current user can access this user
            authorizationService.requireUserAccess(userId);

            User user = userService.getUserById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            return ResponseEntity.ok(Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "email", user.getEmail(),
                    "firstName", user.getFirstName() != null ? user.getFirstName() : "",
                    "lastName", user.getLastName() != null ? user.getLastName() : "",
                    "phoneNumber", user.getPhoneNumber() != null ? user.getPhoneNumber() : "",
                    "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                    "roles", user.getRoles(),
                    "enabled", user.getEnabled()
            ));

        } catch (SecurityException e) {
            log.warn("IDOR attempt detected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve user information"));
        }
    }

    /**
     * Update user profile (without password)
     * PUT /api/users/{userId}
     * Password must be changed via /change-password endpoint
     * Only accessible by the user themselves or admin
     */
    @PutMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateUser(
            @PathVariable Long userId, 
            @RequestBody Map<String, String> updates) {
        try {
            // IDOR Protection
            authorizationService.requireUserAccess(userId);

            User user = userService.getUserById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            // Update only provided fields (null-safe)
            if (updates.containsKey("username") && updates.get("username") != null) {
                String username = updates.get("username").trim();
                if (username.length() < 3 || username.length() > 50) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Username must be between 3 and 50 characters"));
                }
                user.setUsername(username);
            }

            if (updates.containsKey("email") && updates.get("email") != null) {
                String email = updates.get("email").trim();
                if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Invalid email format"));
                }
                user.setEmail(email);
            }

            if (updates.containsKey("firstName") && updates.get("firstName") != null) {
                user.setFirstName(updates.get("firstName").trim());
            }

            if (updates.containsKey("lastName") && updates.get("lastName") != null) {
                user.setLastName(updates.get("lastName").trim());
            }

            if (updates.containsKey("phoneNumber")) {
                user.setPhoneNumber(updates.get("phoneNumber"));
            }

            // KHÔNG cho phép update password qua endpoint này
            if (updates.containsKey("password")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Password cannot be updated here. Use /change-password endpoint"));
            }

            User updatedUser = userRepository.save(user);

            log.info("User profile updated: userId={}, username={}", userId, updatedUser.getUsername());

            return ResponseEntity.ok(Map.of(
                    "message", "User updated successfully",
                    "user", Map.of(
                            "id", updatedUser.getId(),
                            "username", updatedUser.getUsername(),
                            "email", updatedUser.getEmail(),
                            "firstName", updatedUser.getFirstName() != null ? updatedUser.getFirstName() : "",
                            "lastName", updatedUser.getLastName() != null ? updatedUser.getLastName() : "",
                            "phoneNumber", updatedUser.getPhoneNumber() != null ? updatedUser.getPhoneNumber() : "",
                            "avatarUrl", updatedUser.getAvatarUrl() != null ? updatedUser.getAvatarUrl() : ""
                    )
            ));

        } catch (SecurityException e) {
            log.warn("IDOR attempt detected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update user"));
        }
    }

    /**
     * Change password (with IDOR protection)
     * POST /api/users/{userId}/change-password
     * Only accessible by the user themselves
     */
    @PostMapping("/{userId}/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        try {
            // IDOR Protection: Check if current user can change this user's password
            // Note: Admins should NOT be able to change user passwords directly
            Long currentUserId = authorizationService.getCurrentUserId();
            if (!currentUserId.equals(userId)) {
                log.warn("IDOR attempt: User {} tried to change password for User {}", currentUserId, userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied: You can only change your own password"));
            }

            String oldPassword = request.get("oldPassword");
            String newPassword = request.get("newPassword");

            if (oldPassword == null || oldPassword.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Old password is required"));
            }

            if (newPassword == null || newPassword.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "New password is required"));
            }

            if (newPassword.length() < 8) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "New password must be at least 8 characters long"));
            }

            userService.changePassword(userId, oldPassword, newPassword);

            return ResponseEntity.ok(Map.of(
                    "message", "Password changed successfully"
            ));

        } catch (SecurityException e) {
            log.warn("IDOR attempt detected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to change password for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to change password"));
        }
    }

    /**
     * Deactivate user account (Soft Delete)
     * DELETE /api/users/{userId}
     * Sets enabled = false AND deletes avatar from Cloudinary
     * Only accessible by the user themselves or admin
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deactivateAccount(@PathVariable Long userId) {
        try {
            // IDOR Protection: Check if current user can deactivate this account
            authorizationService.requireUserAccess(userId);

            User user = userService.getUserById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            // Check if account is already deactivated
            if (!user.getEnabled()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Account is already deactivated"));
            }

            // Delete avatar from Cloudinary if exists
            boolean avatarDeleted = false;
            if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                String avatarUrl = user.getAvatarUrl();
                avatarDeleted = cloudinaryService.deleteImageByUrl(avatarUrl);

                if (avatarDeleted) {
                    log.info("User {} avatar deleted from Cloudinary: {}", userId, avatarUrl);
                } else {
                    log.warn("Failed to delete user {} avatar from Cloudinary: {}", userId, avatarUrl);
                }
            }

            // Soft delete: Set enabled to false
            user.setEnabled(false);
            userRepository.save(user);

            log.warn("User account deactivated: userId={}, username={}", userId, user.getUsername());

            return ResponseEntity.ok(Map.of(
                    "message", "Account deactivated successfully" +
                               (avatarDeleted ? " with avatar removed from Cloudinary" : ""),
                    "userId", userId,
                    "enabled", false,
                    "avatarDeleted", avatarDeleted
            ));

        } catch (SecurityException e) {
            log.warn("IDOR attempt detected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to deactivate account: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to deactivate account"));
        }
    }

    /**
     * Upload user avatar
     * POST /api/users/{userId}/avatar
     *
     * Form-data:
     * - avatar: file (image) - Ảnh sẽ lưu vào thư mục avatars/user-{userId}/ trên Cloudinary
     *
     * Only accessible by the user themselves or admin
     */
    @PostMapping("/{userId}/avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadAvatar(
            @PathVariable Long userId,
            @RequestParam("avatar") MultipartFile avatar) {
        try {
            // IDOR Protection: Check if current user can upload avatar for this user
            authorizationService.requireUserAccess(userId);

            // Find user
            User user = userService.getUserById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            // Validate avatar
            if (avatar == null || avatar.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Avatar file is required"));
            }

            if (!cloudinaryService.isValidImage(avatar)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid image file. Allowed: JPG, PNG, GIF, WEBP (max 10MB)"));
            }

            // Upload to Cloudinary in avatars/user-{userId}/ folder
            String avatarUrl = cloudinaryService.uploadUserAvatar(avatar, userId);

            // Update user with new avatar URL
            user.setAvatarUrl(avatarUrl);
            User updatedUser = userRepository.save(user);

            log.info("User {} avatar uploaded successfully: {}", userId, avatarUrl);

            return ResponseEntity.ok(Map.of(
                    "message", "Avatar uploaded successfully",
                    "avatarUrl", avatarUrl,
                    "user", Map.of(
                            "id", updatedUser.getId(),
                            "username", updatedUser.getUsername(),
                            "avatarUrl", updatedUser.getAvatarUrl()
                    )
            ));

        } catch (SecurityException e) {
            log.warn("IDOR attempt detected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (IOException e) {
            log.error("Failed to upload avatar: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to upload avatar",
                            "details", e.getMessage()
                    ));

        } catch (Exception e) {
            log.error("Unexpected error uploading avatar: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload avatar"));
        }
    }

    /**
     * Delete user avatar
     * DELETE /api/users/{userId}/avatar
     * Xóa cả trong database VÀ trên Cloudinary
     *
     * Only accessible by the user themselves or admin
     */
    @DeleteMapping("/{userId}/avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteAvatar(@PathVariable Long userId) {
        try {
            // IDOR Protection
            authorizationService.requireUserAccess(userId);

            User user = userService.getUserById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            if (user.getAvatarUrl() == null || user.getAvatarUrl().isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "message", "User has no avatar to delete",
                        "userId", userId
                ));
            }

            String oldAvatarUrl = user.getAvatarUrl();

            // Delete from Cloudinary first
            boolean deletedFromCloudinary = cloudinaryService.deleteImageByUrl(oldAvatarUrl);

            if (deletedFromCloudinary) {
                log.info("User {} avatar deleted from Cloudinary: {}", userId, oldAvatarUrl);
            } else {
                log.warn("Failed to delete user {} avatar from Cloudinary, but will remove from database", userId);
            }

            // Remove avatar URL from user database
            user.setAvatarUrl(null);
            userRepository.save(user);

            log.info("User {} avatar removed from database: {}", userId, oldAvatarUrl);

            return ResponseEntity.ok(Map.of(
                    "message", "Avatar deleted successfully" +
                               (deletedFromCloudinary ? " from both Cloudinary and database" : " from database only"),
                    "userId", userId,
                    "deletedFromCloudinary", deletedFromCloudinary
            ));

        } catch (SecurityException e) {
            log.warn("IDOR attempt detected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to delete avatar: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete avatar"));
        }
    }
}

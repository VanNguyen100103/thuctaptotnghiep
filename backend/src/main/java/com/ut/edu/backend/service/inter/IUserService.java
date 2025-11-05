package com.ut.edu.backend.service.inter;

import com.ut.edu.backend.dto.JwtResponse;
import com.ut.edu.backend.dto.LoginRequest;
import com.ut.edu.backend.dto.RegisterRequest;
import com.ut.edu.backend.model.User;

import java.util.Optional;

/**
 * User Service Interface
 * Manages user authentication and profile operations
 */
public interface IUserService {

    /**
     * Register a new user
     */
    User registerUser(RegisterRequest registerRequest);

    /**
     * Authenticate user and return JWT tokens
     */
    JwtResponse authenticateUser(LoginRequest loginRequest);

    /**
     * Refresh access token
     */
    JwtResponse refreshToken(String refreshToken);

    /**
     * Get user by ID
     */
    Optional<User> getUserById(Long id);

    /**
     * Get user by username
     */
    Optional<User> getUserByUsername(String username);

    /**
     * Get user by email
     */
    Optional<User> getUserByEmail(String email);

    /**
     * Update user profile
     */
    User updateUser(Long id, User updatedUser);

    /**
     * Change password
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * Check if username exists
     */
    boolean existsByUsername(String username);

    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Verify user email with token
     */
    void verifyEmail(String token);


    /**
     * Request password reset
     */
    void requestPasswordReset(String email);

    /**
     * Reset password with token
     */
    void resetPassword(String token, String newPassword);

    /**
     * Verify user email with OTP code
     */
    void verifyOtp(String email, String otpCode);
}

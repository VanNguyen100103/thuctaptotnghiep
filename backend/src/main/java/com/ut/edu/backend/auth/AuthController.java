package com.ut.edu.backend.auth;

import com.ut.edu.backend.user.User;
import com.ut.edu.backend.user.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication Controller
 * Handles user registration, login, and token refresh
 * Note: context-path is already /api, so we only need /auth here
 */
@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    @Autowired
    private UserService userService;

    /**
     * Register new user
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        log.info("Registration request for username: {}", registerRequest.getUsername());

        try {
            User user = userService.registerUser(registerRequest);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "User registered successfully",
                            "username", user.getUsername(),
                            "email", user.getEmail()
                    ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Login user
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Login request for username: {}", loginRequest.getUsername());

        try {
            JwtResponse jwtResponse = userService.authenticateUser(loginRequest);

            return ResponseEntity.ok(jwtResponse);

        } catch (Exception e) {
            log.error("Login failed for username: {}", loginRequest.getUsername(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
    }

    /**
     * Refresh access token
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");

        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Refresh token is required"));
        }

        try {
            JwtResponse jwtResponse = userService.refreshToken(refreshToken);

            return ResponseEntity.ok(jwtResponse);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Check if username is available
     * GET /api/auth/check-username?username=xxx
     */
    @GetMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestParam String username) {
        boolean exists = userService.existsByUsername(username);

        return ResponseEntity.ok(Map.of(
                "available", !exists,
                "message", exists ? "Username already exists" : "Username is available"
        ));
    }

    /**
     * Check if email is available
     * GET /api/auth/check-email?email=xxx
     */
    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        boolean exists = userService.existsByEmail(email);

        return ResponseEntity.ok(Map.of(
                "available", !exists,
                "message", exists ? "Email already exists" : "Email is available"
        ));
    }

    /**
     * Verify OTP code for registration
     * POST /api/auth/verify-otp
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody com.ut.edu.backend.auth.VerifyOtpRequest request) {
        log.info("OTP verification request for email: {}", request.getEmail());

        try {
            userService.verifyOtp(request.getEmail(), request.getOtpCode());

            return ResponseEntity.ok(Map.of(
                    "message", "Account verified successfully. You can now login."
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }


    /**
     * Request password reset
     * POST /api/auth/forgot-password
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");

        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email is required"));
        }

        try {
            userService.requestPasswordReset(email);

            return ResponseEntity.ok(Map.of(
                    "message", "Password reset email sent successfully. Please check your inbox."
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reset password with token
     * POST /api/auth/reset-password
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        String newPassword = request.get("newPassword");

        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Reset token is required"));
        }

        if (newPassword == null || newPassword.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "New password is required"));
        }

        // Basic password validation
        if (newPassword.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Password must be at least 8 characters long"));
        }

        try {
            userService.resetPassword(token, newPassword);

            return ResponseEntity.ok(Map.of(
                    "message", "Password reset successfully. You can now login with your new password."
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Logout user (optional - for frontend to clear tokens)
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser() {
        // In a stateless JWT system, logout is handled on the frontend
        // by removing the tokens from storage
        // Optionally, you could implement a token blacklist here

        return ResponseEntity.ok(Map.of(
                "message", "Logged out successfully"
        ));
    }

    /**
     * Get CSRF token
     * GET /api/auth/csrf-token
     * This endpoint allows clients to obtain a CSRF token before making state-changing requests
     * The token is automatically created by Spring Security and sent as a cookie (XSRF-TOKEN)
     * Clients should include this token in the X-XSRF-TOKEN header for subsequent requests
     */
    @GetMapping("/csrf-token")
    public ResponseEntity<?> getCsrfToken(CsrfToken csrfToken) {
        log.info("CSRF token requested");

        if (csrfToken != null) {
            return ResponseEntity.ok(Map.of(
                    "token", csrfToken.getToken(),
                    "headerName", csrfToken.getHeaderName(),
                    "parameterName", csrfToken.getParameterName(),
                    "message", "CSRF token generated successfully. Use the token from the XSRF-TOKEN cookie or the 'token' field in X-XSRF-TOKEN header."
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "message", "CSRF protection is disabled"
            ));
        }
    }
}

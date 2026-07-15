package com.ut.edu.backend.user;

import com.ut.edu.backend.auth.OtpService;
import com.ut.edu.backend.auth.OtpVerification;

import com.ut.edu.backend.auth.JwtResponse;
import com.ut.edu.backend.auth.LoginRequest;
import com.ut.edu.backend.auth.RegisterRequest;
import com.ut.edu.backend.auth.VerificationToken;
import com.ut.edu.backend.auth.VerificationTokenRepository;
import com.ut.edu.backend.security.JwtTokenProvider;
import com.ut.edu.backend.security.UserPrincipal;
import com.ut.edu.backend.email.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * User Service Implementation
 * Handles user authentication, registration, and profile management
 */
@Service
@Slf4j
@Transactional
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private EmailService emailService;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private com.ut.edu.backend.auth.OtpService otpService;

    public User registerUser(RegisterRequest registerRequest) {
        log.info("Registering new user: {}", registerRequest.getUsername());

        // Check if username already exists
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + registerRequest.getUsername());
        }

        // Check if email already exists
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + registerRequest.getEmail());
        }

        // Create new user (disabled until email verification)
        User user = User.builder()
                .username(registerRequest.getUsername())
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .phoneNumber(registerRequest.getPhoneNumber())
                .roles(new HashSet<>())
                .enabled(false) // Disabled until email verification
                .accountNonLocked(true)
                .failedLoginAttempts(0)
                .build();

        // Add default USER role
        user.addRole(Role.USER);

        User savedUser = userRepository.save(user);
        log.info("User registered successfully (pending OTP verification): {}", savedUser.getUsername());

        // Generate OTP for email verification
        String otpCode = otpService.generateOtp(savedUser, com.ut.edu.backend.auth.OtpVerification.OtpType.REGISTRATION);

        // Send OTP via email
        emailService.sendOtpEmail(savedUser, otpCode);

        return savedUser;
    }

    public JwtResponse authenticateUser(LoginRequest loginRequest) {
        log.info("Authenticating user: {}", loginRequest.getUsername());

        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Generate JWT tokens
        String accessToken = tokenProvider.generateToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        log.info("User authenticated successfully: {}", loginRequest.getUsername());

        // Get full User entity from database
        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Save user session to Redis (full user model + session info)
        if (redisTemplate != null) {
            try {
                String sessionKey = "user:session:" + userPrincipal.getId();

                Map<String, Object> sessionData = new HashMap<>();
                sessionData.put("user", user); // Full User model
                sessionData.put("roles", roles);
                sessionData.put("loginTime", LocalDateTime.now().toString());
                sessionData.put("accessToken", accessToken);
                sessionData.put("refreshToken", refreshToken);

                redisTemplate.opsForValue().set(sessionKey, sessionData, 24, TimeUnit.HOURS);
                log.info("✓ User session saved to Redis: userId={}, key={}", userPrincipal.getId(), sessionKey);
            } catch (Exception e) {
                log.warn("Failed to save session to Redis (non-critical): {}", e.getMessage());
            }
        }

        return JwtResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .id(userPrincipal.getId())
                .username(userPrincipal.getUsername())
                .email(userPrincipal.getEmail())
                .roles(roles)
                .build();
    }

    public JwtResponse refreshToken(String refreshToken) {
        log.info("Refreshing access token");

        // Validate refresh token
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        // Get username from token
        String username = tokenProvider.getUsernameFromToken(refreshToken);

        // Load user details
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        // Create authentication object
        UserPrincipal userPrincipal = UserPrincipal.create(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities()
        );

        // Generate new access token
        String newAccessToken = tokenProvider.generateToken(authentication);

        Set<String> roles = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        log.info("Access token refreshed for user: {}", username);

        return JwtResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(roles)
                .build();
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User updateUser(Long id, User updatedUser) {
        log.info("Updating user: {}", id);

        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        // Update fields
        existingUser.setFirstName(updatedUser.getFirstName());
        existingUser.setLastName(updatedUser.getLastName());
        existingUser.setPhoneNumber(updatedUser.getPhoneNumber());

        // Email update requires verification (not implemented here)
        if (!existingUser.getEmail().equals(updatedUser.getEmail())) {
            if (userRepository.existsByEmail(updatedUser.getEmail())) {
                throw new IllegalArgumentException("Email already exists: " + updatedUser.getEmail());
            }
            existingUser.setEmail(updatedUser.getEmail());
        }

        return userRepository.save(existingUser);
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        log.info("Changing password for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Verify old password
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Old password is incorrect");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("Password changed successfully for user: {}", userId);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public void verifyEmail(String token) {
        log.info("Verifying email with token");

        // Find verification token
        VerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        // Check if token is expired
        if (verificationToken.isExpired()) {
            throw new IllegalArgumentException("Verification token has expired");
        }

        // Check if token is already used
        if (verificationToken.getUsed()) {
            throw new IllegalArgumentException("Verification token has already been used");
        }

        // Check token type
        if (verificationToken.getTokenType() != VerificationToken.TokenType.EMAIL_VERIFICATION) {
            throw new IllegalArgumentException("Invalid token type");
        }

        // Enable user account
        User user = verificationToken.getUser();
        user.setEnabled(true);
        userRepository.save(user);

        // Mark token as used
        verificationToken.setUsed(true);
        verificationTokenRepository.save(verificationToken);

        // Send welcome email
        emailService.sendWelcomeEmail(user);

        log.info("Email verified successfully for user: {}", user.getUsername());
    }

    
    public void requestPasswordReset(String email) {
        log.info("Password reset requested for email: {}", email);

        // Find user by email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

        // Delete existing unused password reset tokens
        verificationTokenRepository.findByUserIdAndTokenType(user.getId(), VerificationToken.TokenType.PASSWORD_RESET)
                .ifPresent(token -> {
                    if (!token.getUsed()) {
                        verificationTokenRepository.delete(token);
                    }
                });

        // Generate password reset token
        String token = UUID.randomUUID().toString();
        VerificationToken resetToken = VerificationToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusHours(1)) // 1-hour expiry for password reset
                .tokenType(VerificationToken.TokenType.PASSWORD_RESET)
                .used(false)
                .build();

        verificationTokenRepository.save(resetToken);

        // Send password reset email
        emailService.sendPasswordResetEmail(user, token);

        log.info("Password reset email sent to: {}", email);
    }

    public void resetPassword(String token, String newPassword) {
        log.info("Resetting password with token");

        // Find verification token
        VerificationToken resetToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid password reset token"));

        // Check if token is expired
        if (resetToken.isExpired()) {
            throw new IllegalArgumentException("Password reset token has expired");
        }

        // Check if token is already used
        if (resetToken.getUsed()) {
            throw new IllegalArgumentException("Password reset token has already been used");
        }

        // Check token type
        if (resetToken.getTokenType() != VerificationToken.TokenType.PASSWORD_RESET) {
            throw new IllegalArgumentException("Invalid token type");
        }

        // Update user password
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0); // Reset failed login attempts
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        verificationTokenRepository.save(resetToken);

        log.info("Password reset successfully for user: {}", user.getUsername());
    }

    public void verifyOtp(String email, String otpCode) {
        log.info("Verifying OTP for email: {}", email);

        // Find user by email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

        // Check if user is already verified
        if (user.getEnabled()) {
            throw new IllegalArgumentException("User is already verified");
        }

        // Verify OTP
        boolean isValid = otpService.verifyOtp(user, otpCode, com.ut.edu.backend.auth.OtpVerification.OtpType.REGISTRATION);

        if (!isValid) {
            throw new IllegalArgumentException("Invalid or expired OTP code");
        }

        // Enable user account
        user.setEnabled(true);
        userRepository.save(user);

        // Send welcome email
        emailService.sendWelcomeEmail(user);

        log.info("OTP verified successfully for user: {}", user.getUsername());
    }
}

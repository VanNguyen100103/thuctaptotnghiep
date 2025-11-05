package com.ut.edu.backend.service.inter;

import com.ut.edu.backend.model.User;

/**
 * Service interface for email operations
 */
public interface IEmailService {

    /**
     * Send verification email to user
     * @param user User to send email to
     * @param token Verification token
     */
    void sendVerificationEmail(User user, String token);

    /**
     * Send password reset email
     * @param user User to send email to
     * @param token Reset token
     */
    void sendPasswordResetEmail(User user, String token);

    /**
     * Send two-factor authentication code
     * @param user User to send email to
     * @param code 2FA code
     */
    void sendTwoFactorAuthCode(User user, String code);

    /**
     * Send order confirmation email
     * @param userEmail User's email address
     * @param orderNumber Order number
     * @param orderDetails Order details
     */
    void sendOrderConfirmationEmail(String userEmail, String orderNumber, String orderDetails);

    /**
     * Send welcome email after successful verification
     * @param user User to send email to
     */
    void sendWelcomeEmail(User user);

    /**
     * Send OTP code for email verification/registration
     * @param user User to send email to
     * @param otpCode 6-digit OTP code
     */
    void sendOtpEmail(User user, String otpCode);
}

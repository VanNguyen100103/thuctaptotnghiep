package com.ut.edu.backend.service.impl;

import com.ut.edu.backend.kafka.KafkaProducerService;
import com.ut.edu.backend.model.User;
import com.ut.edu.backend.service.inter.IEmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of email service
 * Uses Kafka for async email sending
 */
@Service
@Slf4j
public class EmailServiceImpl implements IEmailService {

    private final KafkaProducerService kafkaProducerService;

    // Constructor with optional KafkaProducerService
    public EmailServiceImpl(@org.springframework.beans.factory.annotation.Autowired(required = false) KafkaProducerService kafkaProducerService) {
        this.kafkaProducerService = kafkaProducerService;
        if (kafkaProducerService == null) {
            log.warn("KafkaProducerService is not available. Email sending will be disabled.");
        }
    }

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.name:E-commerce Fashion Store}")
    private String appName;

    @Override
    public void sendVerificationEmail(User user, String token) {
        log.info("Sending verification email to user: {}", user.getEmail());

        if (kafkaProducerService == null) {
            log.warn("Kafka is disabled. Email not sent to: {}", user.getEmail());
            return;
        }

        String verificationLink = frontendUrl + "/verify-email?token=" + token;

        Map<String, Object> emailData = new HashMap<>();
        emailData.put("to", user.getEmail());
        emailData.put("subject", "Verify Your Email - " + appName);
        emailData.put("template", "email-verification");
        emailData.put("username", user.getUsername());
        emailData.put("verificationLink", verificationLink);
        emailData.put("token", token);

        kafkaProducerService.sendEmailEvent(user.getEmail(), "EMAIL_VERIFICATION", emailData);
    }

    @Override
    public void sendPasswordResetEmail(User user, String token) {
        log.info("Sending password reset email to user: {}", user.getEmail());

        if (kafkaProducerService == null) {
            log.warn("Kafka is disabled. Email not sent to: {}", user.getEmail());
            return;
        }

        String resetLink = frontendUrl + "/reset-password?token=" + token;

        Map<String, Object> emailData = new HashMap<>();
        emailData.put("to", user.getEmail());
        emailData.put("subject", "Reset Your Password - " + appName);
        emailData.put("template", "password-reset");
        emailData.put("username", user.getUsername());
        emailData.put("resetLink", resetLink);
        emailData.put("token", token);

        kafkaProducerService.sendEmailEvent(user.getEmail(), "PASSWORD_RESET", emailData);
    }

    @Override
    public void sendTwoFactorAuthCode(User user, String code) {
        log.info("Sending 2FA code to user: {}", user.getEmail());

        if (kafkaProducerService == null) {
            log.warn("Kafka is disabled. Email not sent to: {}", user.getEmail());
            return;
        }

        Map<String, Object> emailData = new HashMap<>();
        emailData.put("to", user.getEmail());
        emailData.put("subject", "Your Two-Factor Authentication Code - " + appName);
        emailData.put("template", "two-factor-auth");
        emailData.put("username", user.getUsername());
        emailData.put("code", code);

        kafkaProducerService.sendEmailEvent(user.getEmail(), "TWO_FACTOR_AUTH", emailData);
    }

    @Override
    public void sendOrderConfirmationEmail(String userEmail, String orderNumber, String orderDetails) {
        log.info("Sending order confirmation email to: {}", userEmail);

        if (kafkaProducerService == null) {
            log.warn("Kafka is disabled. Email not sent to: {}", userEmail);
            return;
        }

        Map<String, Object> emailData = new HashMap<>();
        emailData.put("to", userEmail);
        emailData.put("subject", "Order Confirmation #" + orderNumber + " - " + appName);
        emailData.put("template", "order-confirmation");
        emailData.put("orderNumber", orderNumber);
        emailData.put("orderDetails", orderDetails);

        kafkaProducerService.sendEmailEvent(userEmail, "ORDER_CONFIRMATION", emailData);
    }

    @Override
    public void sendWelcomeEmail(User user) {
        log.info("Sending welcome email to user: {}", user.getEmail());

        if (kafkaProducerService == null) {
            log.warn("Kafka is disabled. Email not sent to: {}", user.getEmail());
            return;
        }

        Map<String, Object> emailData = new HashMap<>();
        emailData.put("to", user.getEmail());
        emailData.put("subject", "Welcome to " + appName);
        emailData.put("template", "welcome");
        emailData.put("username", user.getUsername());
        emailData.put("frontendUrl", frontendUrl);

        kafkaProducerService.sendEmailEvent(user.getEmail(), "WELCOME", emailData);
    }

    @Override
    public void sendOtpEmail(User user, String otpCode) {
        log.info("Sending OTP verification email to user: {}", user.getEmail());

        if (kafkaProducerService == null) {
            log.warn("Kafka is disabled. Email not sent to: {}", user.getEmail());
            return;
        }

        Map<String, Object> emailData = new HashMap<>();
        emailData.put("to", user.getEmail());
        emailData.put("subject", "Your Verification Code - " + appName);
        emailData.put("template", "otp-verification");
        emailData.put("username", user.getUsername());
        emailData.put("otpCode", otpCode);

        kafkaProducerService.sendEmailEvent(user.getEmail(), "OTP_VERIFICATION", emailData);
    }
}

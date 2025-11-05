package com.ut.edu.backend.service.impl;

import com.ut.edu.backend.kafka.KafkaProducerService;
import com.ut.edu.backend.model.User;
import com.ut.edu.backend.service.inter.IEmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of email service
 * Uses Kafka for async email sending, fallback to direct email if Kafka is disabled
 */
@Service
@Slf4j
public class EmailServiceImpl implements IEmailService {

    private final KafkaProducerService kafkaProducerService;
    private final JavaMailSender mailSender;
    private final SendGridEmailService sendGridEmailService;

    // Constructor with optional KafkaProducerService
    public EmailServiceImpl(
        @org.springframework.beans.factory.annotation.Autowired(required = false) KafkaProducerService kafkaProducerService,
        JavaMailSender mailSender,
        SendGridEmailService sendGridEmailService
    ) {
        this.kafkaProducerService = kafkaProducerService;
        this.mailSender = mailSender;
        this.sendGridEmailService = sendGridEmailService;
        if (kafkaProducerService == null) {
            log.warn("KafkaProducerService is not available. Will send emails directly.");
        }
    }

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.name:E-commerce Fashion Store}")
    private String appName;

    @Value("${spring.mail.from:nganhvan1609@gmail.com}")
    private String fromEmail;

    @Value("${spring.mail.from-name:E-commerce Fashion Store}")
    private String fromName;

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
            // Send email directly via SMTP when Kafka is disabled
            log.info("Kafka is disabled. Sending email directly via SMTP to: {}", user.getEmail());
            sendOtpEmailDirect(user, otpCode);
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

    /**
     * Send OTP email directly (fallback when Kafka is disabled)
     * Priority: SendGrid API > SMTP > Log only
     */
    private void sendOtpEmailDirect(User user, String otpCode) {
        String htmlContent = buildOtpVerificationEmailHtml(user.getUsername(), otpCode);
        String subject = "Your Verification Code - " + appName;

        // Try SendGrid first (HTTP API - works on Render free tier)
        if (sendGridEmailService.isConfigured()) {
            log.info("Attempting to send OTP via SendGrid to: {}", user.getEmail());
            boolean sent = sendGridEmailService.sendHtmlEmail(user.getEmail(), subject, htmlContent);

            if (sent) {
                return; // Success!
            } else {
                log.warn("SendGrid failed, falling back to SMTP...");
            }
        }

        // Fallback to SMTP (will fail on Render free tier)
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✓ OTP email sent successfully via SMTP to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("✗ Failed to send OTP email via SMTP to: {}", user.getEmail(), e);
            log.warn("⚠️ Render free tier blocks SMTP ports. OTP code for user {} ({}): {}",
                user.getUsername(), user.getEmail(), otpCode);
            log.info("💡 Configure SENDGRID_API_KEY environment variable to enable email sending");
            // Don't throw exception - allow registration to continue without email
        }
    }

    /**
     * Build HTML for OTP verification email
     */
    private String buildOtpVerificationEmailHtml(String username, String otpCode) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .code-box { background: white; padding: 30px; border-radius: 10px; text-align: center; margin: 20px 0; border: 3px solid #667eea; }
                    .code { font-size: 48px; font-weight: bold; color: #667eea; letter-spacing: 10px; font-family: 'Courier New', monospace; }
                    .footer { text-align: center; margin-top: 20px; color: #666; font-size: 12px; }
                    .info-box { background: #e3f2fd; border-left: 4px solid #2196f3; padding: 15px; margin: 15px 0; border-radius: 5px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🔐 Xác Thực Tài Khoản</h1>
                    </div>
                    <div class="content">
                        <h2>Xin chào %s!</h2>
                        <p>Cảm ơn bạn đã đăng ký tài khoản tại <strong>E-commerce Fashion Store</strong>.</p>
                        <p>Đây là mã xác thực (OTP) để kích hoạt tài khoản của bạn:</p>
                        <div class="code-box">
                            <p style="margin: 0; color: #666; font-size: 14px;">MÃ XÁC THỰC CỦA BẠN</p>
                            <div class="code">%s</div>
                            <p style="margin: 10px 0 0 0; color: #999; font-size: 12px;">Mã có hiệu lực trong 10 phút</p>
                        </div>
                        <div class="info-box">
                            <p style="margin: 5px 0;"><strong>📌 Hướng dẫn:</strong></p>
                            <ol style="margin: 10px 0; padding-left: 20px;">
                                <li>Nhập mã <strong>6 số</strong> trên vào trang xác thực</li>
                                <li>Mã chỉ được sử dụng <strong>1 lần duy nhất</strong></li>
                                <li>Sau 10 phút, mã sẽ <strong>hết hiệu lực</strong></li>
                            </ol>
                        </div>
                        <p><strong>⚠️ Lưu ý bảo mật:</strong></p>
                        <ul style="color: #666;">
                            <li>Không chia sẻ mã này với bất kỳ ai</li>
                            <li>Nhân viên chúng tôi sẽ không bao giờ hỏi mã này</li>
                            <li>Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email</li>
                        </ul>
                    </div>
                    <div class="footer">
                        <p>© 2024 E-commerce Fashion Store. All rights reserved.</p>
                        <p>Nếu bạn không tạo tài khoản này, vui lòng liên hệ: support@fashionstore.com</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(username, otpCode);
    }
}

package com.ut.edu.backend.kafka;

import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.order.Order;

import com.ut.edu.backend.config.KafkaConfig;
import com.ut.edu.backend.email.BrevoEmailService;
import com.ut.edu.backend.email.SendGridEmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Kafka Consumer Service
 * Listens to Kafka topics and processes events asynchronously
 */
@Service
@Slf4j
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "spring.kafka.enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class KafkaConsumerService {

    private final JavaMailSender mailSender;
    private final BrevoEmailService brevoEmailService;
    private final SendGridEmailService sendGridEmailService;

    @Value("${spring.mail.from:nganhvan1609@gmail.com}")
    private String fromEmail;

    @Value("${spring.mail.from-name:E-commerce Fashion Store}")
    private String fromName;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Listen to order created events
     * Process: Send confirmation email, update analytics, etc.
     */
    @KafkaListener(
        topics = KafkaConfig.ORDER_CREATED_TOPIC,
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderCreated(
            @Payload Map<String, Object> orderData,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Received ORDER_CREATED event from partition: {}, offset: {}, order: {}",
            partition, offset, orderData.get("orderNumber"));

        try {
            // Process order created event
            String orderNumber = (String) orderData.get("orderNumber");
            Long orderId = ((Number) orderData.get("orderId")).longValue();

            log.info("Processing order created: {} (ID: {})", orderNumber, orderId);

           

        } catch (Exception e) {
            log.error("Error processing ORDER_CREATED event: {}", orderData, e);
    
        }
    }

    /**
     * Listen to order updated events
     */
    @KafkaListener(
        topics = KafkaConfig.ORDER_UPDATED_TOPIC,
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeOrderUpdated(@Payload Map<String, Object> orderData) {
        log.info("Received ORDER_UPDATED event for order: {}, status: {}",
            orderData.get("orderNumber"), orderData.get("status"));

        try {
            String orderNumber = (String) orderData.get("orderNumber");
            String status = (String) orderData.get("status");

            log.info("Processing order update: {} -> {}", orderNumber, status);

         

        } catch (Exception e) {
            log.error("Error processing ORDER_UPDATED event: {}", orderData, e);
        }
    }

    /**
     * Listen to order cancelled events
     */
    @KafkaListener(
        topics = KafkaConfig.ORDER_CANCELLED_TOPIC,
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeOrderCancelled(@Payload Map<String, Object> orderData) {
        log.info("Received ORDER_CANCELLED event for order: {}, reason: {}",
            orderData.get("orderNumber"), orderData.get("reason"));

        try {
            String orderNumber = (String) orderData.get("orderNumber");
            String reason = (String) orderData.get("reason");

            log.info("Processing order cancellation: {} (Reason: {})", orderNumber, reason);

           

        } catch (Exception e) {
            log.error("Error processing ORDER_CANCELLED event: {}", orderData, e);
        }
    }

    /**
     * Listen to payment completed events
     */
    @KafkaListener(
        topics = KafkaConfig.PAYMENT_COMPLETED_TOPIC,
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumePaymentCompleted(@Payload Map<String, Object> paymentData) {
        log.info("Received PAYMENT_COMPLETED event for order: {}, transaction: {}",
            paymentData.get("orderNumber"), paymentData.get("transactionId"));

        try {
            String orderNumber = (String) paymentData.get("orderNumber");
            String transactionId = (String) paymentData.get("transactionId");
            Double amount = (Double) paymentData.get("amount");

            log.info("Processing payment completion: Order {}, Transaction {}, Amount: ${}",
                orderNumber, transactionId, amount);

            

        } catch (Exception e) {
            log.error("Error processing PAYMENT_COMPLETED event: {}", paymentData, e);
        }
    }

    /**
     * Listen to payment failed events
     */
    @KafkaListener(
        topics = KafkaConfig.PAYMENT_FAILED_TOPIC,
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumePaymentFailed(@Payload Map<String, Object> paymentData) {
        log.info("Received PAYMENT_FAILED event for order: {}, reason: {}",
            paymentData.get("orderNumber"), paymentData.get("reason"));

        try {
            String orderNumber = (String) paymentData.get("orderNumber");
            String reason = (String) paymentData.get("reason");

            log.info("Processing payment failure: Order {} (Reason: {})", orderNumber, reason);

          

        } catch (Exception e) {
            log.error("Error processing PAYMENT_FAILED event: {}", paymentData, e);
        }
    }

    /**
     * Listen to email notification events
     */
    @KafkaListener(
        topics = KafkaConfig.EMAIL_NOTIFICATION_TOPIC,
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeEmailNotification(@Payload Map<String, Object> emailData) {
        String recipientEmail = (String) emailData.get("to");
        if (recipientEmail == null) {
            recipientEmail = (String) emailData.get("recipientEmail");
        }

        log.info("Received EMAIL_NOTIFICATION event for: {}", recipientEmail);

        try {
            String eventType = (String) emailData.get("eventType");
            String subject = (String) emailData.get("subject");

            log.info("Processing {} email to: {}, subject: {}", eventType, recipientEmail, subject);

            // Send email using actual email service provider
            sendEmail(emailData, eventType);

            log.info("Email sent successfully to: {}", recipientEmail);

        } catch (Exception e) {
            log.error("Error processing EMAIL_NOTIFICATION event: {}", emailData, e);
            
        }
    }

    /**
     * Send email with fallback: Brevo API > SendGrid API > Gmail SMTP
     */
    private void sendEmail(Map<String, Object> emailData, String eventType) {
        try {
            String to = (String) emailData.get("to");
            String subject = (String) emailData.get("subject");
            String htmlContent = generateEmailHtml(emailData, eventType);

            if (brevoEmailService.isConfigured()
                    && brevoEmailService.sendHtmlEmail(to, subject, htmlContent)) {
                log.info("✓ Email sent successfully via Brevo to: {} (Type: {})", to, eventType);
                return;
            }

            if (sendGridEmailService.isConfigured()
                    && sendGridEmailService.sendHtmlEmail(to, subject, htmlContent)) {
                log.info("✓ Email sent successfully via SendGrid to: {} (Type: {})", to, eventType);
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = HTML email

            mailSender.send(message);
            log.info("✓ Email sent successfully to: {} (Type: {})", to, eventType);

        } catch (Exception e) {
            log.error("✗ Failed to send email: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Generate HTML email content based on event type
     */
    private String generateEmailHtml(Map<String, Object> emailData, String eventType) {
        String to = (String) emailData.get("to");
        String username = (String) emailData.get("username");

        switch (eventType) {
            case "EMAIL_VERIFICATION":
                String verificationLink = (String) emailData.get("verificationLink");
                return buildVerificationEmailHtml(username, verificationLink);

            case "PASSWORD_RESET":
                String resetLink = (String) emailData.get("resetLink");
                return buildPasswordResetEmailHtml(username, resetLink);

            case "WELCOME":
                return buildWelcomeEmailHtml(username);

            case "ORDER_CONFIRMATION":
                String orderNumber = (String) emailData.get("orderNumber");
                String orderDetails = (String) emailData.get("orderDetails");
                // If orderDetails is provided (from payment webhook), use it directly
                if (orderDetails != null && !orderDetails.isEmpty()) {
                    return orderDetails;
                }
                // Otherwise use simple template
                return buildOrderConfirmationEmailHtml(username, orderNumber);

            case "TWO_FACTOR_AUTH":
                String code = (String) emailData.get("code");
                return build2FAEmailHtml(username, code);

            case "OTP_VERIFICATION":
                String otpCode = (String) emailData.get("otpCode");
                return buildOtpVerificationEmailHtml(username, otpCode);

            default:
                return buildDefaultEmailHtml(to, emailData);
        }
    }

    // Email HTML Templates

    private String buildVerificationEmailHtml(String username, String verificationLink) {
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
                    .button { display: inline-block; padding: 15px 30px; background: #667eea; color: white; text-decoration: none; border-radius: 5px; margin: 20px 0; }
                    .footer { text-align: center; margin-top: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🎉 Xác Thực Email</h1>
                    </div>
                    <div class="content">
                        <h2>Xin chào %s!</h2>
                        <p>Cảm ơn bạn đã đăng ký tài khoản tại <strong>E-commerce Fashion Store</strong>.</p>
                        <p>Vui lòng nhấn vào nút bên dưới để xác thực email của bạn:</p>
                        <div style="text-align: center;">
                            <a href="%s" class="button">Xác Thực Email</a>
                        </div>
                        <p>Hoặc copy link này vào trình duyệt:</p>
                        <p style="background: #fff; padding: 10px; border-radius: 5px; word-break: break-all;">%s</p>
                        <p><strong>Lưu ý:</strong> Link này sẽ hết hạn sau 24 giờ.</p>
                    </div>
                    <div class="footer">
                        <p>© 2024 E-commerce Fashion Store. All rights reserved.</p>
                        <p>Nếu bạn không tạo tài khoản này, vui lòng bỏ qua email này.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(username, verificationLink, verificationLink);
    }

    private String buildPasswordResetEmailHtml(String username, String resetLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #f093fb 0%%, #f5576c 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .button { display: inline-block; padding: 15px 30px; background: #f5576c; color: white; text-decoration: none; border-radius: 5px; margin: 20px 0; }
                    .footer { text-align: center; margin-top: 20px; color: #666; font-size: 12px; }
                    .warning { background: #fff3cd; border-left: 4px solid #ffc107; padding: 10px; margin: 15px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🔒 Đặt Lại Mật Khẩu</h1>
                    </div>
                    <div class="content">
                        <h2>Xin chào %s!</h2>
                        <p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.</p>
                        <p>Nhấn vào nút bên dưới để đặt lại mật khẩu:</p>
                        <div style="text-align: center;">
                            <a href="%s" class="button">Đặt Lại Mật Khẩu</a>
                        </div>
                        <p>Hoặc copy link này vào trình duyệt:</p>
                        <p style="background: #fff; padding: 10px; border-radius: 5px; word-break: break-all;">%s</p>
                        <div class="warning">
                            <strong>⚠️ Lưu ý quan trọng:</strong>
                            <ul>
                                <li>Link này chỉ có hiệu lực trong <strong>1 giờ</strong></li>
                                <li>Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này</li>
                                <li>Không chia sẻ link này với bất kỳ ai</li>
                            </ul>
                        </div>
                    </div>
                    <div class="footer">
                        <p>© 2024 E-commerce Fashion Store. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(username, resetLink, resetLink);
    }

    private String buildWelcomeEmailHtml(String username) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #4facfe 0%%, #00f2fe 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .button { display: inline-block; padding: 15px 30px; background: #4facfe; color: white; text-decoration: none; border-radius: 5px; margin: 20px 0; }
                    .footer { text-align: center; margin-top: 20px; color: #666; font-size: 12px; }
                    .features { background: white; padding: 15px; border-radius: 5px; margin: 15px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🎊 Chào Mừng Bạn!</h1>
                    </div>
                    <div class="content">
                        <h2>Xin chào %s!</h2>
                        <p>🎉 Tài khoản của bạn đã được kích hoạt thành công!</p>
                        <p>Bây giờ bạn có thể đăng nhập và bắt đầu mua sắm những sản phẩm thời trang tuyệt vời tại cửa hàng của chúng tôi.</p>
                        <div class="features">
                            <h3>✨ Những gì bạn có thể làm:</h3>
                            <ul>
                                <li>🛍️ Duyệt hàng ngàn sản phẩm thời trang</li>
                                <li>❤️ Lưu sản phẩm yêu thích</li>
                                <li>🚚 Theo dõi đơn hàng của bạn</li>
                                <li>💳 Thanh toán an toàn với PayPal</li>
                                <li>🎁 Nhận ưu đãi độc quyền</li>
                            </ul>
                        </div>
                        <div style="text-align: center;">
                            <a href="%s" class="button">Bắt Đầu Mua Sắm</a>
                        </div>
                    </div>
                    <div class="footer">
                        <p>© 2024 E-commerce Fashion Store. All rights reserved.</p>
                        <p>Cảm ơn bạn đã tin tưởng chúng tôi! 💙</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(username, frontendUrl);
    }

    private String buildOrderConfirmationEmailHtml(String username, String orderNumber) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #43e97b 0%%, #38f9d7 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .button { display: inline-block; padding: 15px 30px; background: #43e97b; color: white; text-decoration: none; border-radius: 5px; margin: 20px 0; }
                    .order-box { background: white; padding: 20px; border-radius: 5px; margin: 15px 0; border-left: 4px solid #43e97b; }
                    .footer { text-align: center; margin-top: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>✅ Đơn Hàng Đã Được Xác Nhận!</h1>
                    </div>
                    <div class="content">
                        <h2>Xin chào %s!</h2>
                        <p>Cảm ơn bạn đã đặt hàng tại <strong>E-commerce Fashion Store</strong>!</p>
                        <div class="order-box">
                            <h3>📦 Thông Tin Đơn Hàng</h3>
                            <p><strong>Mã đơn hàng:</strong> <span style="color: #43e97b; font-size: 18px;">%s</span></p>
                            <p>Chúng tôi đang xử lý đơn hàng của bạn và sẽ gửi email cập nhật khi đơn hàng được giao.</p>
                        </div>
                        <div style="text-align: center;">
                            <a href="%s/orders/%s" class="button">Xem Chi Tiết Đơn Hàng</a>
                        </div>
                    </div>
                    <div class="footer">
                        <p>© 2024 E-commerce Fashion Store. All rights reserved.</p>
                        <p>Cần hỗ trợ? Liên hệ: support@fashionstore.com</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(username, orderNumber, frontendUrl, orderNumber);
    }

    private String build2FAEmailHtml(String username, String code) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #fa709a 0%%, #fee140 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .code-box { background: white; padding: 30px; border-radius: 10px; text-align: center; margin: 20px 0; border: 2px dashed #fa709a; }
                    .code { font-size: 36px; font-weight: bold; color: #fa709a; letter-spacing: 5px; }
                    .footer { text-align: center; margin-top: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🔐 Mã Xác Thực 2FA</h1>
                    </div>
                    <div class="content">
                        <h2>Xin chào %s!</h2>
                        <p>Đây là mã xác thực hai yếu tố (2FA) của bạn:</p>
                        <div class="code-box">
                            <p style="margin: 0; color: #666;">Mã xác thực của bạn:</p>
                            <div class="code">%s</div>
                            <p style="margin: 10px 0 0 0; color: #999; font-size: 12px;">Mã này có hiệu lực trong 10 phút</p>
                        </div>
                        <p><strong>⚠️ Lưu ý:</strong> Không chia sẻ mã này với bất kỳ ai. Nhân viên của chúng tôi sẽ không bao giờ yêu cầu mã này.</p>
                    </div>
                    <div class="footer">
                        <p>© 2024 E-commerce Fashion Store. All rights reserved.</p>
                        <p>Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(username, code);
    }

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

    private String buildDefaultEmailHtml(String to, Map<String, Object> emailData) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 10px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="content">
                        <h2>Thông báo từ E-commerce Fashion Store</h2>
                        <p>Xin chào %s,</p>
                        <p>%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(to, emailData.toString());
    }

    /**
     * Listen to inventory update events
     */
    @KafkaListener(
        topics = KafkaConfig.INVENTORY_UPDATE_TOPIC,
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeInventoryUpdate(@Payload Map<String, Object> inventoryData) {
        log.info("Received INVENTORY_UPDATE event for product: {}, operation: {}",
            inventoryData.get("productId"), inventoryData.get("operation"));

        try {
            Long productId = ((Number) inventoryData.get("productId")).longValue();
            String sku = (String) inventoryData.get("sku");
            Integer quantityChange = (Integer) inventoryData.get("quantityChange");
            String operation = (String) inventoryData.get("operation");

            log.info("Processing inventory update: Product {} (SKU: {}), {} by {}",
                productId, sku, operation, quantityChange);

        

        } catch (Exception e) {
            log.error("Error processing INVENTORY_UPDATE event: {}", inventoryData, e);
        }
    }
}

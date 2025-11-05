package com.ut.edu.backend.service.impl;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * SendGrid email service for sending emails via HTTP API
 * Works on Render free tier (no SMTP port restrictions)
 */
@Service
@Slf4j
public class SendGridEmailService {

    @Value("${sendgrid.api.key:}")
    private String sendGridApiKey;

    @Value("${spring.mail.from:nganhvan1609@gmail.com}")
    private String fromEmail;

    @Value("${spring.mail.from-name:E-commerce Fashion Store}")
    private String fromName;

    /**
     * Send HTML email via SendGrid API
     *
     * @param toEmail Recipient email address
     * @param subject Email subject
     * @param htmlContent HTML content
     * @return true if sent successfully, false otherwise
     */
    public boolean sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        // Check if SendGrid API key is configured
        if (sendGridApiKey == null || sendGridApiKey.isEmpty()) {
            log.warn("SendGrid API key is not configured. Email not sent to: {}", toEmail);
            return false;
        }

        try {
            Email from = new Email(fromEmail, fromName);
            Email to = new Email(toEmail);
            Content content = new Content("text/html", htmlContent);
            Mail mail = new Mail(from, subject, to, content);

            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();

            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("✓ Email sent successfully via SendGrid to: {}", toEmail);
                return true;
            } else {
                log.error("✗ SendGrid API returned status {}: {}", response.getStatusCode(), response.getBody());
                return false;
            }

        } catch (IOException e) {
            log.error("✗ Failed to send email via SendGrid to: {}", toEmail, e);
            return false;
        }
    }

    /**
     * Check if SendGrid is properly configured
     */
    public boolean isConfigured() {
        return sendGridApiKey != null && !sendGridApiKey.isEmpty();
    }
}

package com.ut.edu.backend.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Brevo email service for sending emails via HTTP API
 * Works on Render free tier (no SMTP port restrictions)
 * Free tier: 300 emails/day, no expiration (replaces SendGrid whose free plan was retired in 2025)
 */
@Service
@Slf4j
public class BrevoEmailService {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${brevo.api.key:}")
    private String brevoApiKey;

    @Value("${spring.mail.from:nganhvan1609@gmail.com}")
    private String fromEmail;

    @Value("${spring.mail.from-name:E-commerce Fashion Store}")
    private String fromName;

    /**
     * Send HTML email via Brevo API
     *
     * @param toEmail Recipient email address
     * @param subject Email subject
     * @param htmlContent HTML content
     * @return true if sent successfully, false otherwise
     */
    public boolean sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        if (!isConfigured()) {
            log.warn("Brevo API key is not configured. Email not sent to: {}", toEmail);
            return false;
        }

        try {
            Map<String, Object> body = Map.of(
                    "sender", Map.of("email", fromEmail, "name", fromName),
                    "to", List.of(Map.of("email", toEmail)),
                    "subject", subject,
                    "htmlContent", htmlContent
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BREVO_API_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("api-key", brevoApiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("✓ Email sent successfully via Brevo to: {}", toEmail);
                return true;
            } else {
                log.error("✗ Brevo API returned status {}: {}", response.statusCode(), response.body());
                return false;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("✗ Interrupted while sending email via Brevo to: {}", toEmail, e);
            return false;
        } catch (Exception e) {
            log.error("✗ Failed to send email via Brevo to: {}", toEmail, e);
            return false;
        }
    }

    /**
     * Check if Brevo is properly configured
     */
    public boolean isConfigured() {
        return brevoApiKey != null && !brevoApiKey.isEmpty();
    }
}

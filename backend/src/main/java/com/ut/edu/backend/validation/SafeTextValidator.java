package com.ut.edu.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Validator implementation for XSS prevention
 * Detects potentially dangerous HTML/JavaScript content
 */
@Slf4j
public class SafeTextValidator implements ConstraintValidator<SafeText, String> {

    private boolean allowSafeHTML;

    // Patterns for detecting XSS attacks
    private static final Pattern[] DANGEROUS_PATTERNS = {
        // Script tags
        Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("<script[^>]*>", Pattern.CASE_INSENSITIVE),

        // Event handlers
        Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE),

        // JavaScript protocol
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("data:text/html", Pattern.CASE_INSENSITIVE),

        // iframe, embed, object tags
        Pattern.compile("<iframe[^>]*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<object[^>]*>", Pattern.CASE_INSENSITIVE),

        // Expression() and eval()
        Pattern.compile("expression\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),

        // Import statements
        Pattern.compile("@import", Pattern.CASE_INSENSITIVE),

        // Meta refresh
        Pattern.compile("<meta[^>]*http-equiv", Pattern.CASE_INSENSITIVE)
    };

    // Safe HTML tags (only if allowSafeHTML is true)
    private static final Pattern SAFE_HTML_PATTERN = Pattern.compile(
        "</?(?:b|i|u|strong|em|p|br|ul|ol|li|a|span|div)[^>]*>",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public void initialize(SafeText constraintAnnotation) {
        this.allowSafeHTML = constraintAnnotation.allowSafeHTML();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null or empty values are considered valid
        if (value == null || value.trim().isEmpty()) {
            return true;
        }

        // Check for dangerous patterns
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(value).find()) {
                log.warn("Potential XSS attack detected in input: {}", value.substring(0, Math.min(50, value.length())));

                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        "Input contains potentially dangerous content (scripts, event handlers, or dangerous HTML tags)"
                ).addConstraintViolation();

                return false;
            }
        }

        // If safe HTML is not allowed, check for any HTML tags
        if (!allowSafeHTML) {
            Pattern anyHTMLTag = Pattern.compile("<[^>]+>", Pattern.CASE_INSENSITIVE);
            if (anyHTMLTag.matcher(value).find()) {
                // Check if it's a safe tag
                String cleanedValue = SAFE_HTML_PATTERN.matcher(value).replaceAll("");
                if (anyHTMLTag.matcher(cleanedValue).find()) {
                    log.warn("HTML tags detected in input (not allowed): {}", value.substring(0, Math.min(50, value.length())));

                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate(
                            "HTML tags are not allowed in this field"
                    ).addConstraintViolation();

                    return false;
                }
            }
        }

        return true;
    }
}

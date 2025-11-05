package com.ut.edu.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Validator implementation for SQL Injection prevention
 * Detects common SQL injection patterns
 */
@Slf4j
public class NoSQLInjectionValidator implements ConstraintValidator<NoSQLInjection, String> {

    // Patterns for detecting SQL injection attempts
    private static final Pattern[] SQL_INJECTION_PATTERNS = {
        // SQL Comments
        Pattern.compile("--", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/\\*.*?\\*/", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),

        // SQL Keywords that could indicate injection
        Pattern.compile(".*\\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|EXECUTE|UNION|SCRIPT)\\b.*", Pattern.CASE_INSENSITIVE),

        // SQL operators
        Pattern.compile(".*['\";].*OR.*['\";].*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*['\";].*AND.*['\";].*", Pattern.CASE_INSENSITIVE),

        // Common injection patterns
        Pattern.compile(".*\\bOR\\b.*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*'\\s*OR\\s*'1'\\s*=\\s*'1", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*'\\s*OR\\s*1\\s*=\\s*1", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bAND\\b.*=.*", Pattern.CASE_INSENSITIVE),

        // Hex encoding
        Pattern.compile("0x[0-9a-fA-F]+", Pattern.CASE_INSENSITIVE),

        // SQL string concatenation
        Pattern.compile(".*\\|\\|.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\+.*'.*", Pattern.CASE_INSENSITIVE),

        // UNION attacks
        Pattern.compile(".*UNION.*SELECT.*", Pattern.CASE_INSENSITIVE),

        // Time-based blind SQL injection
        Pattern.compile(".*WAITFOR\\s+DELAY.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*SLEEP\\s*\\(.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*BENCHMARK\\s*\\(.*", Pattern.CASE_INSENSITIVE),

        // Stacked queries
        Pattern.compile(".*;\\s*(SELECT|INSERT|UPDATE|DELETE|DROP).*", Pattern.CASE_INSENSITIVE)
    };

    @Override
    public void initialize(NoSQLInjection constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null or empty values are considered valid (use @NotNull/@NotBlank for required fields)
        if (value == null || value.trim().isEmpty()) {
            return true;
        }

        // Check against SQL injection patterns
        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(value).matches()) {
                log.warn("Potential SQL injection detected: {}", value);

                // Customize error message
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        "Input contains forbidden characters or patterns that could be used for SQL injection"
                ).addConstraintViolation();

                return false;
            }
        }

        return true;
    }
}

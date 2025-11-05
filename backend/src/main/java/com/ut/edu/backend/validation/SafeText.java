package com.ut.edu.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validation annotation for safe text input (XSS prevention)
 * Ensures text doesn't contain potentially dangerous HTML/JavaScript
 */
@Documented
@Constraint(validatedBy = SafeTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeText {

    String message() default "Input contains potentially dangerous content (HTML/JavaScript)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Allow certain safe HTML tags (e.g., <b>, <i>, <p>)
     */
    boolean allowSafeHTML() default false;
}

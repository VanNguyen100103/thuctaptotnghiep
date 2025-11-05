package com.ut.edu.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validation annotation to prevent SQL injection in input fields
 * Checks for common SQL injection patterns
 */
@Documented
@Constraint(validatedBy = NoSQLInjectionValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoSQLInjection {

    String message() default "Input contains potential SQL injection patterns";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

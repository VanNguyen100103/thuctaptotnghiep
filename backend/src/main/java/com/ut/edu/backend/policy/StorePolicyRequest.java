package com.ut.edu.backend.policy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update body for a store policy. */
public record StorePolicyRequest(
        @NotBlank(message = "Policy title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,
        @NotBlank(message = "Policy content is required")
        @Size(max = 5000, message = "Content must be at most 5000 characters")
        String content,
        Integer displayOrder) {
}

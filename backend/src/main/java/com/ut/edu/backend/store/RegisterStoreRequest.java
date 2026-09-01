package com.ut.edu.backend.store;

import com.ut.edu.backend.validation.SafeText;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Store onboarding request: creates the store (tenant), its OWNER account
 * and a 14-day FREE_TRIAL subscription in one call.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterStoreRequest {

    // --- Store ---

    @NotBlank(message = "Store name is required")
    @Size(min = 2, max = 100, message = "Store name must be between 2 and 100 characters")
    @SafeText(message = "Store name contains dangerous content")
    private String storeName;

    @NotBlank(message = "Store slug is required")
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug must be lowercase letters, digits and hyphens")
    @Size(min = 2, max = 100, message = "Store slug must be between 2 and 100 characters")
    private String storeSlug;

    @SafeText(message = "Phone contains dangerous content")
    private String storePhone;

    @SafeText(message = "Address contains dangerous content")
    private String storeAddress;

    // --- Owner account ---

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @SafeText(message = "Username contains dangerous content")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    private String password;

    @NotBlank(message = "First name is required")
    @SafeText(message = "First name contains dangerous content")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @SafeText(message = "Last name contains dangerous content")
    private String lastName;

    @SafeText(message = "Phone number contains dangerous content")
    private String phoneNumber;
}

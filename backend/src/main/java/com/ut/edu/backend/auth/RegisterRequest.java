package com.ut.edu.backend.auth;

import com.ut.edu.backend.validation.SafeText;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registration request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

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

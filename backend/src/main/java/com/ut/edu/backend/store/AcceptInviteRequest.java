package com.ut.edu.backend.store;

import com.ut.edu.backend.validation.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Invitee redeems the emailed token and creates their staff account.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcceptInviteRequest {

    @NotBlank(message = "Invitation token is required")
    private String token;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @SafeText(message = "Username contains dangerous content")
    private String username;

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

package com.ut.edu.backend.store;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Owner invites a staff member into their store.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InviteStaffRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    /** MANAGER or STAFF - validated in the service (OWNER/SUPER_ADMIN rejected). */
    @NotNull(message = "Store role is required")
    private StoreRole storeRole;
}

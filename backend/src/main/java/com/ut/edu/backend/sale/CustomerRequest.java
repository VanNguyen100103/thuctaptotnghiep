package com.ut.edu.backend.sale;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** Create/update body for a customer - code is server-generated, never taken from the client. */
public record CustomerRequest(
        @NotBlank(message = "Customer name is required") String name,
        String phone,
        String email,
        LocalDate dateOfBirth,
        String gender,
        String address,
        String region,
        String ward,
        String groupName,
        String note) {
}

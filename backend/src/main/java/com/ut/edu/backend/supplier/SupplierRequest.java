package com.ut.edu.backend.supplier;

import jakarta.validation.constraints.NotBlank;

/** Create/update body for a supplier - code is server-generated, never taken from the client. */
public record SupplierRequest(
        @NotBlank(message = "Supplier name is required") String name,
        String phone,
        String email,
        String address,
        String taxCode,
        String note) {
}

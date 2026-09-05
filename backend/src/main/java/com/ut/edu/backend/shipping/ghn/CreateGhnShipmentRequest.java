package com.ut.edu.backend.shipping.ghn;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateGhnShipmentRequest(
        @NotBlank(message = "Recipient name is required") String toName,
        @NotBlank(message = "Recipient phone is required") String toPhone,
        @NotBlank(message = "Address is required") String toAddress,
        @NotNull(message = "Province is required") Integer toProvinceId,
        @NotBlank String toProvinceName,
        @NotNull(message = "District is required") Integer toDistrictId,
        @NotBlank String toDistrictName,
        @NotBlank(message = "Ward is required") String toWardCode,
        @NotBlank String toWardName,
        @NotNull(message = "Weight is required") @Min(value = 1, message = "Weight must be at least 1g") Integer weightGrams,
        String note) {
}

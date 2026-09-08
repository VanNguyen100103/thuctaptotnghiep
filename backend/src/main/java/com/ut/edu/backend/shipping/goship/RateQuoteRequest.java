package com.ut.edu.backend.shipping.goship;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * What the register needs priced. Only the destination and the parcel: the
 * pickup end is the shop's own address, which comes from configuration
 * rather than from the caller (see GoshipShipmentService).
 */
public record RateQuoteRequest(
        @NotBlank(message = "Tỉnh/thành là bắt buộc") String toCityId,
        @NotBlank(message = "Quận/huyện là bắt buộc") String toDistrictId,
        @NotNull(message = "Cân nặng là bắt buộc")
        @Min(value = 1, message = "Cân nặng tối thiểu 1g") Integer weightGrams,
        @Min(value = 1, message = "Chiều dài tối thiểu 1cm") Integer lengthCm,
        @Min(value = 1, message = "Chiều rộng tối thiểu 1cm") Integer widthCm,
        @Min(value = 1, message = "Chiều cao tối thiểu 1cm") Integer heightCm,
        /** "Thu hộ" - zero for an order already paid at the counter. */
        BigDecimal codAmount,
        /** Declared value, used for insurance pricing. Falls back to the COD amount when absent, which is what it is worth in practice. */
        BigDecimal declaredAmount) {
}

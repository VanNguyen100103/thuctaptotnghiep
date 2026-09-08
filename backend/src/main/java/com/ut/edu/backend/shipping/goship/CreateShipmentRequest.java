package com.ut.edu.backend.shipping.goship;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * A booking. The names alongside every address code are carried rather than
 * looked up again: they are what the shipment list and the receipt print,
 * and re-resolving a code months later would depend on Goship still
 * answering for it.
 */
public record CreateShipmentRequest(
        /** The rate the cashier accepted, straight from the quote. Goship prices the booking from this, not from the numbers we send. */
        @NotBlank(message = "Chưa chọn hãng vận chuyển") String rateId,
        @NotBlank(message = "Tên người nhận là bắt buộc") String toName,
        @NotBlank(message = "Số điện thoại là bắt buộc") String toPhone,
        @NotBlank(message = "Địa chỉ là bắt buộc") String toAddress,
        @NotBlank(message = "Tỉnh/thành là bắt buộc") String toCityId,
        @NotBlank String toCityName,
        @NotBlank(message = "Quận/huyện là bắt buộc") String toDistrictId,
        @NotBlank String toDistrictName,
        @NotBlank(message = "Phường/xã là bắt buộc") String toWardId,
        @NotBlank String toWardName,
        @NotNull(message = "Cân nặng là bắt buộc")
        @Min(value = 1, message = "Cân nặng tối thiểu 1g") Integer weightGrams,
        @Min(value = 1, message = "Chiều dài tối thiểu 1cm") Integer lengthCm,
        @Min(value = 1, message = "Chiều rộng tối thiểu 1cm") Integer widthCm,
        @Min(value = 1, message = "Chiều cao tối thiểu 1cm") Integer heightCm,
        BigDecimal codAmount,
        /**
         * "Khai giá" - what the carrier compensates against if the parcel is
         * lost or damaged. Null or zero means the sender declared nothing and
         * carries that risk themselves.
         */
        BigDecimal declaredAmount,
        /**
         * "Người gửi trả phí". Null counts as true: the register has already
         * told the customer what they owe, so billing them again at the door
         * has to be an explicit choice rather than a default.
         */
        Boolean senderPaysShipping,
        String note,
        /**
         * Carried over from the chosen quote purely to display. Goship's
         * booking response names the carrier authoritatively but says
         * nothing about which service level was picked or how long it is
         * expected to take, and re-quoting server-side to recover two
         * labels would cost an extra round trip on every checkout.
         */
        String service,
        String expected) {
}

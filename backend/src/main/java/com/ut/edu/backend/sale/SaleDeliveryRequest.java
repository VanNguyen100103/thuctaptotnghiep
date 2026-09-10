package com.ut.edu.backend.sale;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The "Bán giao hàng" half of a checkout: who the parcel goes to and where.
 *
 * Present only when the register is on the delivery tab. When it is,
 * {@link SaleService} writes an Order alongside the invoice, because a sale
 * that has a named recipient and an address is something the shop still has
 * to fulfil - and it looks for it under "Đặt hàng", not under "Hóa đơn".
 *
 * The address arrives as names rather than ids: Goship's own city/district/
 * ward ids mean nothing outside a booking call, and this is the copy the shop
 * reads on the order list and the courier reads on the label.
 */
public record SaleDeliveryRequest(
        @NotBlank(message = "Chưa nhập tên người nhận") String recipientName,
        @NotBlank(message = "Chưa nhập số điện thoại người nhận") String recipientPhone,
        /** Street line, already joined with the "Thôn/Ấp" and "Khu phố" detail boxes. */
        @NotBlank(message = "Chưa nhập địa chỉ người nhận") String address,
        /** Tỉnh/Thành phố. */
        String provinceName,
        /** Quận/Huyện. */
        String districtName,
        /** Phường/Xã. */
        String wardName,
        String note,
        /**
         * "Phí giao hàng" - what the customer is charged for the delivery,
         * which the register seeds from the chosen rate and the cashier can
         * then change. Distinct from what the carrier bills the shop.
         */
        @DecimalMin(value = "0.0", message = "shippingFee cannot be negative") BigDecimal shippingFee,
        /** "Thu hộ tiền" - the courier collects at the door, so nothing has been paid yet. */
        boolean codEnabled,
        /** The carrier the cashier picked, when they booked one through the price comparison. */
        String carrierName,
        /** "Thời gian giao hàng" - when the shop promised it; null when nobody said. */
        LocalDateTime expectedDeliveryAt) {
}

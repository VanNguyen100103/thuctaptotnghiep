package com.ut.edu.backend.shipping.goship;

import jakarta.validation.constraints.NotBlank;

/**
 * The three Goship codes that say where a store's parcels are collected.
 *
 * Only codes: the courier's contact and the street come from the store's own
 * name, phone and address, and the place names come from Goship whenever
 * they are needed. Storing a copy of "Quận Bình Thạnh" here would give the
 * app a second answer to a question Goship already answers, and eventually a
 * stale one.
 */
public record SavePickupAddressRequest(
        @NotBlank(message = "Chưa chọn Tỉnh/Thành") String cityId,
        @NotBlank(message = "Chưa chọn Quận/Huyện") String districtId,
        @NotBlank(message = "Chưa chọn Phường/Xã") String wardId) {
}

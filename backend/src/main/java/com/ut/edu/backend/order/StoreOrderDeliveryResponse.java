package com.ut.edu.backend.order;

import com.ut.edu.backend.shipping.goship.Shipment;

import java.math.BigDecimal;

/**
 * "Giao hàng" - what the cashier chose on the delivery panel, as the order
 * screens show it.
 *
 * Read from the booked Shipment rather than copied onto the Order, because
 * Goship keeps revising it: the fee and the tracking code both arrive after
 * the booking, by webhook. A copy would be right for about a minute.
 *
 * Absent on an order with no booking behind it - a storefront order, a
 * "Tự giao hàng" sale the shop delivers on its own legs, or one whose booking
 * failed. The order still names its carrier in that case; it just cannot say
 * what the parcel costs or where it is.
 */
public record StoreOrderDeliveryResponse(
        Long shipmentId,
        /** Goship's own reference for the booking, printed on the label. */
        String orderRef,
        String carrierName,
        String carrierShortName,
        /** The service level picked out of the price comparison - "Tiết kiệm", "Nhanh". */
        String service,
        /** Goship gives a phrase, not a date: "Dự kiến giao 6 ngày". */
        String expected,
        String trackingNumber,
        /** Where the parcel is, in the carrier's own words. */
        String statusText,
        /** What the carrier charges for this parcel. Never part of the order's total - see senderPaysShipping. */
        BigDecimal shippingFee,
        /** True when the shop is absorbing the fee; false when the courier collects it from the recipient. */
        boolean senderPaysShipping,
        /** "Thu hộ" - what the courier collects at the door. */
        BigDecimal codAmount,
        Integer weightGrams,
        String toAddress) {

    static StoreOrderDeliveryResponse from(Shipment shipment) {
        if (shipment == null) {
            return null;
        }
        return new StoreOrderDeliveryResponse(
                shipment.getId(),
                shipment.getOrderRef(),
                shipment.getCarrierName(),
                shipment.getCarrierShortName(),
                shipment.getService(),
                shipment.getExpected(),
                shipment.getTrackingNumber(),
                shipment.getStatusText(),
                shipment.getShippingFee(),
                !Boolean.FALSE.equals(shipment.getSenderPaysShipping()),
                shipment.getCodAmount(),
                shipment.getWeightGrams(),
                fullAddress(shipment));
    }

    private static String fullAddress(Shipment shipment) {
        return String.join(", ",
                shipment.getToAddress(),
                shipment.getToWardName(),
                shipment.getToDistrictName(),
                shipment.getToCityName());
    }
}

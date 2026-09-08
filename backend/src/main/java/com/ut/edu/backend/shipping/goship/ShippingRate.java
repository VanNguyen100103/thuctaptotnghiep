package com.ut.edu.backend.shipping.goship;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * One carrier's offer for a route - a row in the price comparison the
 * cashier chooses from.
 *
 * `id` is the whole point: Goship's booking call takes it back verbatim, so
 * a quote here and the shipment it becomes are the same decision. Passing a
 * price around instead would let the two drift apart.
 *
 * Only the fields the register actually shows are lifted out of Goship's
 * much larger fee breakdown (oil_fee, remote_area_fee, return_fee and the
 * rest). `totalFee` is the number that matters: it already contains the
 * carrier's charge and Goship's own service fee, which are billed together.
 */
public record ShippingRate(
        String id,
        String carrierName,
        String carrierShortName,
        String carrierLogo,
        String service,
        /** Free text like "Dự kiến giao 2 ngày" - Goship gives a phrase, not a date. */
        String expected,
        BigDecimal totalFee,
        BigDecimal codFee,
        BigDecimal serviceFee,
        /** Goship's own delivery-success statistic for this carrier, shown so a cashier can weigh price against reliability. */
        Double successPercent) {

    public static ShippingRate from(JsonNode node) {
        return new ShippingRate(
                node.path("id").asText(null),
                node.path("carrier_name").asText(null),
                node.path("carrier_short_name").asText(null),
                node.path("carrier_logo").asText(null),
                node.path("service").asText(null),
                node.path("expected").asText(null),
                decimal(node, "total_fee"),
                decimal(node, "cod_fee"),
                decimal(node, "service_fee"),
                node.path("report").path("success_percent").isNumber()
                        ? node.path("report").path("success_percent").asDouble()
                        : null);
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : BigDecimal.ZERO;
    }
}

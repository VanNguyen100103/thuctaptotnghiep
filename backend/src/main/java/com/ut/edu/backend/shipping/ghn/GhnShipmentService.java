package com.ut.edu.backend.shipping.ghn;

import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.user.User;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Business logic for the GHN sandbox test-shipment tool. See GhnShipment's
 * doc comment / V21 migration for why this is standalone rather than part of
 * the real Order flow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GhnShipmentService {

    /** GHN's fixed "Hàng nhẹ" service (order-level length/width/height/weight) - see application.properties comment; the alternative (5 = "Hàng nặng") prices from per-item dimensions instead, not needed for this POC form. */
    private static final int SERVICE_TYPE_ID = 2;
    private static final int DEFAULT_LENGTH_CM = 20;
    private static final int DEFAULT_WIDTH_CM = 20;
    private static final int DEFAULT_HEIGHT_CM = 10;
    /** 2 = recipient pays the shipping fee on delivery - the common default for COD-style e-commerce test orders. */
    private static final int PAYMENT_TYPE_ID = 2;
    private static final String REQUIRED_NOTE = "KHONGCHOXEMHANG";
    /** GHN's documented initial state - the create-order response itself carries no status field, so this is a placeholder until the first refresh/webhook confirms it. */
    private static final String INITIAL_STATUS = "ready_to_pick";

    private final GhnClient ghnClient;
    private final GhnShipmentRepository ghnShipmentRepository;
    private final TenantGuard tenantGuard;

    public List<Map<String, Object>> listProvinces() {
        return extractSorted(ghnClient.getProvinces(), "ProvinceID", "ProvinceName");
    }

    public List<Map<String, Object>> listDistricts(int provinceId) {
        return extractSorted(ghnClient.getDistricts(provinceId), "DistrictID", "DistrictName");
    }

    public List<Map<String, Object>> listWards(int districtId) {
        return extractSorted(ghnClient.getWards(districtId), "WardCode", "WardName");
    }

    private List<Map<String, Object>> extractSorted(JsonNode root, String idField, String nameField) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode node : root.path("data")) {
            items.add(Map.of("id", node.path(idField).asText(), "name", node.path(nameField).asText()));
        }
        items.sort(Comparator.comparing(m -> (String) m.get("name")));
        return items;
    }

    public GhnShipment createShipment(Long storeId, User createdBy, CreateGhnShipmentRequest request) {
        String clientOrderCode = "TT" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4);

        Map<String, Object> body = Map.ofEntries(
                Map.entry("client_order_code", clientOrderCode),
                Map.entry("to_name", request.toName().trim()),
                Map.entry("to_phone", request.toPhone().trim()),
                Map.entry("to_address", request.toAddress().trim()),
                Map.entry("to_ward_code", request.toWardCode()),
                Map.entry("to_district_id", request.toDistrictId()),
                Map.entry("weight", request.weightGrams()),
                Map.entry("length", DEFAULT_LENGTH_CM),
                Map.entry("width", DEFAULT_WIDTH_CM),
                Map.entry("height", DEFAULT_HEIGHT_CM),
                Map.entry("service_type_id", SERVICE_TYPE_ID),
                Map.entry("payment_type_id", PAYMENT_TYPE_ID),
                Map.entry("required_note", REQUIRED_NOTE),
                Map.entry("items", List.of(Map.of("name", "Hàng hóa test", "quantity", 1))));

        JsonNode response = ghnClient.createOrder(body);
        JsonNode data = response.path("data");

        GhnShipment shipment = GhnShipment.builder()
                .store(tenantGuard.currentStoreRef())
                .createdBy(createdBy)
                .clientOrderCode(clientOrderCode)
                .ghnOrderCode(data.path("order_code").asText())
                .toName(request.toName().trim())
                .toPhone(request.toPhone().trim())
                .toAddress(request.toAddress().trim())
                .toProvinceId(request.toProvinceId())
                .toProvinceName(request.toProvinceName())
                .toDistrictId(request.toDistrictId())
                .toDistrictName(request.toDistrictName())
                .toWardCode(request.toWardCode())
                .toWardName(request.toWardName())
                .weightGrams(request.weightGrams())
                .note(request.note())
                .status(INITIAL_STATUS)
                .shippingFee(toBigDecimal(data.path("total_fee")))
                .expectedDeliveryTime(toLocalDateTime(data.path("expected_delivery_time")))
                .build();

        return ghnShipmentRepository.save(shipment);
    }

    public GhnShipment refreshStatus(Long id) {
        GhnShipment shipment = findStoreShipment(id);
        JsonNode data = ghnClient.getOrderDetail(shipment.getGhnOrderCode()).path("data");
        shipment.setStatus(data.path("status").asText(shipment.getStatus()));
        if (!data.path("total_fee").isMissingNode()) {
            shipment.setShippingFee(toBigDecimal(data.path("total_fee")));
        }
        return ghnShipmentRepository.save(shipment);
    }

    /** POST target for GHN's push webhook (see GhnWebhookController) - runs with no tenant context, so it looks the shipment up by GHN's own globally-unique order_code. Always succeeds silently on an unrecognized code so GHN doesn't retry forever. */
    public void handleWebhook(JsonNode payload) {
        String orderCode = payload.path("OrderCode").asText(null);
        String status = payload.path("Status").asText(null);
        if (orderCode == null || status == null) {
            log.warn("GHN webhook payload missing OrderCode/Status: {}", payload);
            return;
        }
        ghnShipmentRepository.findByGhnOrderCode(orderCode).ifPresentOrElse(
                shipment -> {
                    shipment.setStatus(status);
                    ghnShipmentRepository.save(shipment);
                    log.info("GHN webhook: {} -> {}", orderCode, status);
                },
                () -> log.warn("GHN webhook for unknown order_code {}", orderCode));
    }

    public List<GhnShipment> list(String query, String status) {
        Specification<GhnShipment> spec = Specification.where(null);
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("ghnOrderCode")), like),
                    cb.like(cb.lower(root.get("clientOrderCode")), like),
                    cb.like(cb.lower(root.get("toName")), like)));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        return ghnShipmentRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private GhnShipment findStoreShipment(Long id) {
        return ghnShipmentRepository.findById(id)
                .filter(s -> tenantGuard.isCurrentStore(s.getStore()))
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + id));
    }

    private static BigDecimal toBigDecimal(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(node.asText("0"));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static LocalDateTime toLocalDateTime(JsonNode node) {
        if (node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(node.asText()).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

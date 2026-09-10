package com.ut.edu.backend.shipping.goship;

import com.ut.edu.backend.order.Order;
import com.ut.edu.backend.order.OrderRepository;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.StoreRepository;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.user.User;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the app does with Goship: price a route across every carrier,
 * book the one the cashier picked, and keep the row up to date afterwards.
 *
 * Goship wants the pickup end spelled out on every quote and booking, where
 * GHN inferred it from the shop id. All of it comes off the Store row: the
 * name, phone and street the receipt already prints, plus the three Goship
 * address codes the owner picks under Đối tác giao hàng.
 *
 * Per store rather than per deployment, because this is a multi-store
 * platform - one pickup address in configuration would have shipped every
 * store's parcels from whichever shop the deployment was set up for. A store
 * that has not set its codes cannot quote at all, which is a better failure
 * than quietly collecting from the wrong address.
 *
 * A pickup point marked default in the Goship dashboard does not remove the
 * need for this - that applies to orders raised on their website, while
 * every API example carries address_from explicitly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoshipShipmentService {

    /** Sized for a small parcel. Only used when the caller sends nothing - the POS panel supplies its own. */
    private static final int DEFAULT_LENGTH_CM = 20;
    private static final int DEFAULT_WIDTH_CM = 20;
    private static final int DEFAULT_HEIGHT_CM = 10;

    /**
     * Goship's payer codes. The register has already told the customer what
     * they owe by the time a booking is made, so the shop paying is the
     * default and billing the recipient at the door is a deliberate choice
     * the cashier makes on screen.
     */
    private static final int PAYER_SHOP = 1;
    private static final int PAYER_RECIPIENT = 0;

    /** BEST Express's "recall" mode - not something this app offers. */
    private static final int NOT_A_RECALL = 0;



    private final GoshipClient goshipClient;
    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final TenantGuard tenantGuard;

    // ---- address master data ----

    public List<Map<String, Object>> listCities() {
        return namedList(goshipClient.cities());
    }

    public List<Map<String, Object>> listDistricts(String cityId) {
        return namedList(goshipClient.districts(cityId));
    }

    public List<Map<String, Object>> listWards(String districtId) {
        return namedList(goshipClient.wards(districtId));
    }

    /** Cities and districts come back with string ids, wards with numeric ones - asText flattens both so callers never have to care. */
    private List<Map<String, Object>> namedList(JsonNode root) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode node : GoshipClient.payload(root)) {
            items.add(Map.of("id", node.path("id").asText(), "name", node.path("name").asText()));
        }
        items.sort(Comparator.comparing(m -> (String) m.get("name")));
        return items;
    }

    // ---- pricing ----

    /**
     * Every carrier's price for one route, cheapest first.
     *
     * Deduplicated on the way out: Goship's sandbox returns the same carrier
     * and service twice for some routes, and two identical rows are a
     * choice the cashier cannot meaningfully make.
     */
    public List<ShippingRate> quote(RateQuoteRequest request) {
        Store store = requirePickupAddress();

        BigDecimal cod = request.codAmount() != null ? request.codAmount() : BigDecimal.ZERO;
        BigDecimal declared = request.declaredAmount() != null ? request.declaredAmount() : cod;

        Map<String, Object> body = Map.of("shipment", Map.of(
                "address_from", Map.of("city", store.getGoshipCityId(), "district", store.getGoshipDistrictId()),
                "address_to", Map.of("city", request.toCityId(), "district", request.toDistrictId()),
                "parcel", parcel(request.weightGrams(), request.lengthCm(), request.widthCm(), request.heightCm(),
                        cod, declared, null)));

        List<ShippingRate> rates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode node : GoshipClient.payload(goshipClient.rates(body))) {
            ShippingRate rate = ShippingRate.from(node);
            if (rate.id() == null || !seen.add(rate.carrierShortName() + "|" + rate.service() + "|" + rate.totalFee())) {
                continue;
            }
            rates.add(rate);
        }
        rates.sort(Comparator.comparing(ShippingRate::totalFee));
        return rates;
    }

    // ---- booking ----

    public Shipment createShipment(User createdBy, CreateShipmentRequest request) {
        Store store = requirePickupAddress();

        String orderRef = "TT" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4);
        BigDecimal cod = request.codAmount() != null ? request.codAmount() : BigDecimal.ZERO;
        // Not defaulted to the COD amount: "Khai giá" left unticked means the
        // sender declared nothing, and quietly insuring the parcel anyway
        // would bill them for cover they did not ask for.
        BigDecimal declared = request.declaredAmount() != null ? request.declaredAmount() : BigDecimal.ZERO;

        Map<String, Object> shipment = new HashMap<>();
        shipment.put("rate", request.rateId());
        shipment.put("payer", Boolean.FALSE.equals(request.senderPaysShipping()) ? PAYER_RECIPIENT : PAYER_SHOP);
        shipment.put("order_id", orderRef);
        shipment.put("is_recall", NOT_A_RECALL);
        shipment.put("address_from", Map.of(
                "name", orEmpty(store.getName()),
                "phone", orEmpty(store.getPhone()),
                "street", orEmpty(store.getAddress()),
                "ward", store.getGoshipWardId(),
                "district", store.getGoshipDistrictId(),
                "city", store.getGoshipCityId()));
        shipment.put("address_to", Map.of(
                "name", request.toName().trim(),
                "phone", request.toPhone().trim(),
                "street", request.toAddress().trim(),
                "ward", request.toWardId(),
                "district", request.toDistrictId(),
                "city", request.toCityId()));
        InspectionPolicy inspection = request.inspectionPolicy() != null
                ? request.inspectionPolicy()
                : InspectionPolicy.NO_INSPECTION;
        shipment.put("parcel", parcel(request.weightGrams(), request.lengthCm(), request.widthCm(), request.heightCm(),
                cod, declared, deliveryNote(inspection, request.note())));

        JsonNode data = GoshipClient.payload(goshipClient.createShipment(Map.of("shipment", shipment)));

        // Booking is asynchronous at Goship's end: this responds 200 even
        // when the carrier later refuses the order, so the row is saved with
        // whatever came back and the webhook is what settles it.
        Shipment saved = Shipment.builder()
                .store(tenantGuard.currentStoreRef())
                .createdBy(createdBy)
                .orderRef(orderRef)
                .goshipId(text(data, "id"))
                .trackingNumber(text(data, "tracking_number"))
                .carrierName(text(data, "carrier"))
                .carrierShortName(text(data, "carrier_short_name"))
                .service(request.service())
                .expected(request.expected())
                .rateId(request.rateId())
                .toName(request.toName().trim())
                .toPhone(request.toPhone().trim())
                .toAddress(request.toAddress().trim())
                .toCityId(request.toCityId())
                .toCityName(request.toCityName())
                .toDistrictId(request.toDistrictId())
                .toDistrictName(request.toDistrictName())
                .toWardId(request.toWardId())
                .toWardName(request.toWardName())
                .weightGrams(request.weightGrams())
                .lengthCm(orDefault(request.lengthCm(), DEFAULT_LENGTH_CM))
                .widthCm(orDefault(request.widthCm(), DEFAULT_WIDTH_CM))
                .heightCm(orDefault(request.heightCm(), DEFAULT_HEIGHT_CM))
                .codAmount(cod)
                .senderPaysShipping(!Boolean.FALSE.equals(request.senderPaysShipping()))
                .shippingFee(decimal(data, "fee"))
                .statusCode(data.path("shipment_status").isNumber() ? data.path("shipment_status").asInt() : null)
                .statusText(text(data, "shipment_status_txt"))
                .note(request.note())
                .inspectionPolicy(inspection)
                .build();

        linkToOrder(saved, request.orderId());
        return shipmentRepository.save(saved);
    }

    /**
     * Hangs the parcel off its order, and copies the two facts the "Đặt hàng"
     * list filters and sorts on - the carrier and the tracking code - onto the
     * order itself.
     *
     * Only those two. Everything else the delivery panel decided (the service,
     * the expected time, the fee, who pays it, the COD) stays here on the
     * shipment and is read through this link, because Goship's webhook goes on
     * revising the fee and the tracking code after the booking - a second copy
     * would start disagreeing with the first the moment it did.
     *
     * A cross-tenant or unknown order id is ignored rather than fatal: the
     * parcel is already booked with the carrier at this point, and refusing to
     * save it would lose a real shipment over a bad reference.
     */
    private void linkToOrder(Shipment shipment, Long orderId) {
        if (orderId == null) {
            return;
        }
        orderRepository.findById(orderId)
                .filter(order -> tenantGuard.isCurrentStore(order.getStore()))
                .ifPresentOrElse(order -> {
                    shipment.setOrder(order);
                    if (shipment.getCarrierName() != null && !shipment.getCarrierName().isBlank()) {
                        order.setShippingCarrier(shipment.getCarrierName());
                    }
                    if (shipment.getTrackingNumber() != null && !shipment.getTrackingNumber().isBlank()) {
                        order.setTrackingNumber(shipment.getTrackingNumber());
                    }
                    orderRepository.save(order);
                }, () -> log.warn("Shipment {} references unknown order {}", shipment.getOrderRef(), orderId));
    }

    /**
     * What the courier is told: the inspection rule first, then whatever the
     * cashier typed. That order matters - a carrier label truncating a long
     * note should keep the instruction rather than lose it.
     */
    private static String deliveryNote(InspectionPolicy inspection, String note) {
        return note != null && !note.isBlank() ? inspection.note() + " - " + note : inspection.note();
    }

    private Map<String, Object> parcel(Integer weightGrams, Integer lengthCm, Integer widthCm, Integer heightCm,
                                       BigDecimal cod, BigDecimal declared, String metadata) {
        Map<String, Object> parcel = new HashMap<>();
        parcel.put("cod", cod);
        parcel.put("amount", declared);
        parcel.put("weight", weightGrams);
        parcel.put("length", orDefault(lengthCm, DEFAULT_LENGTH_CM));
        parcel.put("width", orDefault(widthCm, DEFAULT_WIDTH_CM));
        parcel.put("height", orDefault(heightCm, DEFAULT_HEIGHT_CM));
        if (metadata != null && !metadata.isBlank()) {
            parcel.put("metadata", metadata);
        }
        return parcel;
    }

    /**
     * The current store, with its pickup point already checked.
     *
     * Without the codes a booking would pass validation here and then be
     * refused by Goship with a message about a field the cashier has never
     * heard of, so it is stopped with one that says where to fix it.
     */
    public Store requirePickupAddress() {
        Store store = storeRepository.findById(tenantGuard.requireStore())
                .orElseThrow(() -> new GoshipApiException("Không tìm thấy cửa hàng hiện tại"));
        if (isBlank(store.getGoshipCityId()) || isBlank(store.getGoshipDistrictId()) || isBlank(store.getGoshipWardId())) {
            throw new GoshipApiException(
                    "Chưa cấu hình điểm lấy hàng - vào Đối tác giao hàng để chọn Tỉnh/Quận/Phường của cửa hàng");
        }
        return store;
    }

    /** Reads the pickup point without demanding it be set - for the settings form, which exists to fill it in. */
    public Store currentStore() {
        return storeRepository.findById(tenantGuard.requireStore())
                .orElseThrow(() -> new GoshipApiException("Không tìm thấy cửa hàng hiện tại"));
    }

    /** Saves the three codes the owner picked. Names are not stored: they are Goship's to resolve, and a stale copy would only mislead. */
    @org.springframework.transaction.annotation.Transactional
    public Store savePickupAddress(String cityId, String districtId, String wardId) {
        Store store = currentStore();
        store.setGoshipCityId(cityId);
        store.setGoshipDistrictId(districtId);
        store.setGoshipWardId(wardId);
        return storeRepository.save(store);
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }

    // ---- keeping a shipment current ----

    /** Manual "refresh" for the shipment list - the fallback whenever the webhook has not arrived, which in the sandbox is always (it does not fire them). */
    public Shipment refreshStatus(Long id) {
        Shipment shipment = findStoreShipment(id);
        String code = shipment.getGoshipId() != null ? shipment.getGoshipId() : shipment.getOrderRef();
        JsonNode found = firstOf(GoshipClient.payload(goshipClient.searchShipment(code)));
        if (found == null) {
            log.warn("Goship has no shipment for {}", code);
            return shipment;
        }
        applyRemoteState(shipment, found);
        return shipmentRepository.save(shipment);
    }

    /** The search endpoint answers with a list for a range query and an object for a single code - take whichever shape arrives. */
    private JsonNode firstOf(JsonNode node) {
        if (node.isArray()) {
            return node.isEmpty() ? null : node.get(0);
        }
        return node.isObject() && !node.isEmpty() ? node : null;
    }

    private void applyRemoteState(Shipment shipment, JsonNode data) {
        if (data.path("shipment_status").isNumber()) {
            shipment.setStatusCode(data.path("shipment_status").asInt());
        }
        setIfPresent(text(data, "shipment_status_txt"), shipment::setStatusText);
        setIfPresent(text(data, "tracking_number"), shipment::setTrackingNumber);
        setIfPresent(text(data, "carrier"), shipment::setCarrierName);
        setIfPresent(text(data, "carrier_short_name"), shipment::setCarrierShortName);
        setIfPresent(text(data, "id"), shipment::setGoshipId);
        if (data.path("fee").isNumber()) {
            shipment.setShippingFee(data.path("fee").decimalValue());
        }
    }

    /**
     * Goship's push. Runs with no tenant context, so the shipment is found
     * by an identifier that is unique across every store: their own code
     * first, then our order reference, which is the only handle that exists
     * for a booking whose Goship id never came back.
     *
     * An unrecognised shipment is logged and dropped rather than treated as
     * an error - not every webhook on this account has to be ours.
     */
    public void handleWebhook(JsonNode payload) {
        String goshipCode = payload.path("gcode").asText(null);
        String orderRef = payload.path("order_id").asText(null);

        Shipment shipment = null;
        if (goshipCode != null && !goshipCode.isBlank()) {
            shipment = shipmentRepository.findByGoshipId(goshipCode).orElse(null);
        }
        if (shipment == null && orderRef != null && !orderRef.isBlank()) {
            shipment = shipmentRepository.findByOrderRef(orderRef).orElse(null);
        }
        if (shipment == null) {
            log.warn("Goship webhook for unknown shipment (gcode={}, order_id={})", goshipCode, orderRef);
            return;
        }

        if (payload.path("status").isNumber()) {
            shipment.setStatusCode(payload.path("status").asInt());
        }
        setIfPresent(payload.path("status_text").asText(null), shipment::setStatusText);
        setIfPresent(payload.path("code").asText(null), shipment::setTrackingNumber);
        setIfPresent(goshipCode, shipment::setGoshipId);
        if (payload.path("fee").isNumber()) {
            shipment.setShippingFee(payload.path("fee").decimalValue());
        }
        shipmentRepository.save(shipment);
        // Booking is asynchronous, so the tracking code often arrives here
        // rather than in the create response - and the order screen shows it.
        Order order = shipment.getOrder();
        if (order != null && shipment.getTrackingNumber() != null && !shipment.getTrackingNumber().isBlank()) {
            order.setTrackingNumber(shipment.getTrackingNumber());
            orderRepository.save(order);
        }
        log.info("Goship webhook: {} -> {} ({})", shipment.getOrderRef(),
                shipment.getStatusCode(), shipment.getStatusText());
    }

    // ---- listing ----

    public List<Shipment> list(String query, Integer statusCode) {
        Specification<Shipment> spec = Specification.where(null);
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("goshipId")), like),
                    cb.like(cb.lower(root.get("trackingNumber")), like),
                    cb.like(cb.lower(root.get("orderRef")), like),
                    cb.like(cb.lower(root.get("toName")), like)));
        }
        if (statusCode != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("statusCode"), statusCode));
        }
        return shipmentRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private Shipment findStoreShipment(Long id) {
        return shipmentRepository.findById(id)
                .filter(s -> tenantGuard.isCurrentStore(s.getStore()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vận đơn: " + id));
    }

    // ---- small helpers ----

    private static void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : BigDecimal.ZERO;
    }

    private static int orDefault(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

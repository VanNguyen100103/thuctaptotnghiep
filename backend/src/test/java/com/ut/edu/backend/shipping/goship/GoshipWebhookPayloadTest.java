package com.ut.edu.backend.shipping.goship;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ut.edu.backend.order.Order;
import com.ut.edu.backend.order.OrderDeliverySync;
import com.ut.edu.backend.order.OrderRepository;
import com.ut.edu.backend.order.OrderStatus;
import com.ut.edu.backend.store.StoreRepository;
import com.ut.edu.backend.store.TenantGuard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Goship quotes its numbers. The status-change webhook carries
 * {@code "status": "901"} and {@code "fee": "35650"} as JSON strings, and a
 * reader that only accepts real numbers takes the webhook, returns 200, and
 * changes nothing - the worst kind of failure, because everything looks fine
 * from both ends while the order silently stops following its parcel.
 *
 * The payload below is Goship's own documented example, kept verbatim.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoshipWebhookPayloadTest {

    private static final String DOCUMENTED_PAYLOAD = """
            {
              "gcode": "GS6ZE234V6",
              "code": "GAPBLXAE",
              "order_id": "SML-003749",
              "weight": "2360.0000000000005",
              "fee": "35650",
              "cod": "0",
              "payer": "0",
              "status": "901",
              "status_text": "Chờ lấy hàng",
              "message": "Chờ shipper qua lấy hàng",
              "tracking_url": "https://donhang.ghn.vn/?order_code=GAPBLXAE",
              "carrier_short_name": "ghn",
              "update_time": 1735700400
            }
            """;

    @Mock private GoshipClient goshipClient;
    @Mock private ShipmentRepository shipmentRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderDeliverySync deliverySync;
    @Mock private TenantGuard tenantGuard;

    @InjectMocks
    private GoshipShipmentService service;

    private Shipment shipment;
    private Order order;

    @BeforeEach
    void setUp() {
        order = Order.builder().id(1L).orderNumber("DH000004").status(OrderStatus.PENDING_COD).build();
        shipment = Shipment.builder()
                .id(5L).orderRef("SML-003749").order(order)
                .shippingFee(BigDecimal.ZERO).codAmount(BigDecimal.ZERO)
                .build();
        when(shipmentRepository.findByGoshipId("GS6ZE234V6")).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static JsonNode payload() throws Exception {
        return new ObjectMapper().readTree(DOCUMENTED_PAYLOAD);
    }

    @Test
    void quotedStatusAndFeeAreRead() throws Exception {
        service.handleWebhook(payload());

        assertThat(shipment.getStatusCode()).isEqualTo(901);
        assertThat(shipment.getShippingFee()).isEqualByComparingTo("35650");
        assertThat(shipment.getStatusText()).isEqualTo("Chờ lấy hàng");
        // "code" is the carrier's own tracking code, which is what a customer quotes.
        assertThat(shipment.getTrackingNumber()).isEqualTo("GAPBLXAE");
    }

    @Test
    void theOrderIsToldWhatTheParcelJustDid() throws Exception {
        service.handleWebhook(payload());

        // The whole point of the webhook: the status reaches the order, with
        // the code parsed out of the string it arrived as. By id, not entity -
        // see OrderDeliverySync#applyShipmentUpdate.
        verify(deliverySync).applyShipmentUpdate(1L, 901, "GAPBLXAE");
    }

    @Test
    void anUnknownShipmentIsDroppedRatherThanFailing() throws Exception {
        when(shipmentRepository.findByGoshipId(any())).thenReturn(Optional.empty());
        when(shipmentRepository.findByOrderRef(any())).thenReturn(Optional.empty());

        // Not every webhook on this Goship account has to be ours.
        service.handleWebhook(payload());

        verify(shipmentRepository, never()).save(any());
        verifyNoInteractions(deliverySync);
    }
}

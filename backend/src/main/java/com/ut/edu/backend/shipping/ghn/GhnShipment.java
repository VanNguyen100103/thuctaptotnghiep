package com.ut.edu.backend.shipping.ghn;

import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;
import com.ut.edu.backend.user.User;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single test shipment created through GHN's sandbox API from the Đối tác
 * giao hàng > Tích hợp > Lịch sử giao hàng tool - see V21 migration for why
 * this is standalone rather than hanging off Order.
 */
@Entity
@Table(name = "ghn_shipments", indexes = {
    @Index(name = "idx_ghn_shipments_store", columnList = "store_id"),
    @Index(name = "idx_ghn_shipments_ghn_order_code", columnList = "ghn_order_code")
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store", "createdBy"})
public class GhnShipment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    @JsonIgnore
    private User createdBy;

    /** Our own reference code, sent to GHN as client_order_code so the webhook/detail lookup doesn't depend solely on their order_code existing yet. */
    @Column(name = "client_order_code", nullable = false, length = 50)
    private String clientOrderCode;

    /** GHN's own tracking code ("mã vận đơn"), returned by shipping-order/create. */
    @Column(name = "ghn_order_code", length = 50)
    private String ghnOrderCode;

    @Column(name = "to_name", nullable = false, length = 200)
    private String toName;

    @Column(name = "to_phone", nullable = false, length = 30)
    private String toPhone;

    @Column(name = "to_address", nullable = false, length = 500)
    private String toAddress;

    @Column(name = "to_province_id", nullable = false)
    private Integer toProvinceId;

    @Column(name = "to_province_name", nullable = false, length = 100)
    private String toProvinceName;

    @Column(name = "to_district_id", nullable = false)
    private Integer toDistrictId;

    @Column(name = "to_district_name", nullable = false, length = 100)
    private String toDistrictName;

    @Column(name = "to_ward_code", nullable = false, length = 20)
    private String toWardCode;

    @Column(name = "to_ward_name", nullable = false, length = 100)
    private String toWardName;

    @Column(name = "weight_grams", nullable = false)
    private Integer weightGrams;

    @Column(length = 500)
    private String note;

    /** GHN's own status string (e.g. "ready_to_pick", "delivering", "delivered") - stored verbatim rather than mapped to a local enum, since GHN's status list evolves independently of this app. */
    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "shipping_fee", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "expected_delivery_time")
    private LocalDateTime expectedDeliveryTime;
}

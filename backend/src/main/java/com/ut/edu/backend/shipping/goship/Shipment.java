package com.ut.edu.backend.shipping.goship;

import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;
import com.ut.edu.backend.user.User;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * One shipment booked through Goship, from either the POS "Bán giao hàng"
 * checkout or the Đối tác giao hàng test form. See the V28 migration for why
 * this replaced the GHN-specific table rather than extending it.
 *
 * The carrier is a property of the row rather than of the integration: the
 * same table holds a J&T booking next to a Viettel Post one, because the
 * cashier picked whichever rate suited that order.
 */
@Entity
@Table(name = "shipments", indexes = {
    @Index(name = "idx_shipments_store", columnList = "store_id"),
    @Index(name = "idx_shipments_goship_id", columnList = "goship_id"),
    @Index(name = "idx_shipments_order_ref", columnList = "order_ref")
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store", "createdBy"})
public class Shipment extends BaseEntity {

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

    /**
     * Our own reference, sent to Goship as order_id. Booking is asynchronous
     * there, so a webhook can arrive describing a shipment whose Goship id
     * we have not stored yet - this is the handle that always exists.
     */
    @Column(name = "order_ref", nullable = false, length = 50)
    private String orderRef;

    /** Goship's own id for the booking, e.g. "GSL9VBJ2Y6". */
    @Column(name = "goship_id", length = 50)
    private String goshipId;

    /** The carrier's own tracking code - what a customer would type on the carrier's website. */
    @Column(name = "tracking_number", length = 50)
    private String trackingNumber;

    /** Display name of the carrier that won this order, e.g. "Giao Hàng Nhanh (v3)". */
    @Column(name = "carrier_name", length = 100)
    private String carrierName;

    /** Goship's machine name for the same carrier, e.g. "ghnv3" - what code should branch on. */
    @Column(name = "carrier_short_name", length = 50)
    private String carrierShortName;

    /** The carrier's service level, e.g. "Nhanh". */
    @Column(name = "service", length = 100)
    private String service;

    /** The rate this was booked at. Kept for reconciliation: it is the only link back to the quote the cashier accepted. */
    @Column(name = "rate_id", length = 200)
    private String rateId;

    @Column(name = "to_name", nullable = false, length = 200)
    private String toName;

    @Column(name = "to_phone", nullable = false, length = 30)
    private String toPhone;

    @Column(name = "to_address", nullable = false, length = 500)
    private String toAddress;

    // Goship's address codes are strings ("100000"), unlike GHN's numeric
    // ids, and ward ids are numbers - all held as text so the two kinds
    // never have to be told apart.

    @Column(name = "to_city_id", nullable = false, length = 20)
    private String toCityId;

    @Column(name = "to_city_name", nullable = false, length = 100)
    private String toCityName;

    @Column(name = "to_district_id", nullable = false, length = 20)
    private String toDistrictId;

    @Column(name = "to_district_name", nullable = false, length = 100)
    private String toDistrictName;

    @Column(name = "to_ward_id", nullable = false, length = 20)
    private String toWardId;

    @Column(name = "to_ward_name", nullable = false, length = 100)
    private String toWardName;

    @Column(name = "weight_grams", nullable = false)
    private Integer weightGrams;

    @Column(name = "length_cm")
    private Integer lengthCm;

    @Column(name = "width_cm")
    private Integer widthCm;

    @Column(name = "height_cm")
    private Integer heightCm;

    /** "Thu hộ" - what the courier collects from the recipient. Zero for an order already paid at the counter. */
    @Column(name = "cod_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal codAmount = BigDecimal.ZERO;

    @Column(name = "shipping_fee", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    /** Goship's numeric status (900 = đơn mới, 901 = chờ lấy hàng, ...). Stored raw: their list evolves independently of this app. */
    @Column(name = "status_code")
    private Integer statusCode;

    /** The Vietnamese label Goship sends alongside the code, so the UI needs no status table of its own. */
    @Column(name = "status_text", length = 100)
    private String statusText;

    /** Free text from the quote, e.g. "Dự kiến giao 2 ngày" - a phrase rather than a date, which is all Goship gives. */
    @Column(name = "expected", length = 200)
    private String expected;

    @Column(length = 500)
    private String note;
}

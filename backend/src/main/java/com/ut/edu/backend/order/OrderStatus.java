package com.ut.edu.backend.order;

import com.ut.edu.backend.payment.Payment;

/**
 * Where an order stands.
 *
 * Two vocabularies, deliberately in one list. The first few are this system's
 * own, and describe an order that has no parcel yet - a customer has ordered
 * and possibly paid, and nobody has picked anything up. Nothing at a carrier
 * can produce them.
 *
 * The rest are the carrier's, one per Goship status code, because a shop
 * chasing a parcel asks Goship's question - is it picked up, in a warehouse,
 * out with a courier, came back - and folding four of those into one "Đang
 * giao hàng" took the answer away. See GoshipShipmentStatus for the mapping.
 */
public enum OrderStatus {

    // ---- before there is a parcel: this system's own ----
    PENDING,             // "Chờ xác nhận" - created, awaiting payment
    PAYMENT_PENDING,     // "Chờ thanh toán" - payment initiated (async gateway - PayPal/MoMo)
    PENDING_COD,         // "COD chờ giao" - COD confirmed, stock committed, cash collected on delivery
    PAID,                // "Đã thanh toán"
    PROCESSING,          // "Đang xử lý" - being prepared, or a booking Goship has not sent on yet (900)

    // ---- the carrier's, one per Goship code ----
    AWAITING_PICKUP,     // 901 "Chờ lấy hàng"
    PICKING,             // 902 "Lấy hàng" - courier on the way to collect
    PICKED_UP,           // 903 "Đã lấy hàng" - out of the shop
    AT_WAREHOUSE,        // 918 "Đang lưu kho"
    IN_TRANSIT,          // 919 "Đang vận chuyển"
    SHIPPED,             // 904 "Đang giao hàng" - out with a courier for the customer
    DELIVERY_FAILED,     // 906 "Giao thất bại" - an attempt, normally retried
    PARTIALLY_DELIVERED, // 916 "Giao hàng một phần"
    RETURNING,           // 907 "Đang chuyển hoàn" - on its way back, not back yet
    DELIVERED,           // 905 "Giao thành công"

    /**
     * 912 "Chờ thanh toán COD" - the courier has collected the cash and Goship
     * owes it to the shop.
     *
     * Not the same thing as PENDING_COD, however similar the Vietnamese reads.
     * That one is before delivery with the money still at the customer's; this
     * one is after delivery with the money already collected. Merging them
     * would make a delivered order report as undelivered and unpaid.
     */
    COD_SETTLEMENT,

    // ---- endings ----
    COMPLETED,           // 913 "Hoàn thành" - the carrier is done with it
    RETURNED,            // 908 "Chuyển hoàn" - back with the shop, the customer never got it
    LOST,                // 917 "Thất lạc hàng"
    CANCELLED,           // 914 "Đơn hủy" - by the shop, the customer, or the carrier
    REFUNDED,            // "Đã hoàn tiền"
    FAILED               // 1000 "Đơn lỗi", and payment failures that never became a parcel
}

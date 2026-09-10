package com.ut.edu.backend.order;

/**
 * "Kênh bán" - where an order came from.
 *
 * KiotViet encodes this in the order code itself (DHZMA000020 from Zalo,
 * DHFBP000018 from a Facebook page). Storing it instead keeps the code a
 * plain sequence and lets the shop correct a channel after the fact, which
 * is exactly what its "Sửa người nhận đặt, kênh bán, ghi chú" action is for.
 */
public enum SalesChannel {
    /** The store's own online shop - where a customer checked out themselves. */
    STOREFRONT,
    /** "Bán trực tiếp" - rung up at the register on the "Bán giao hàng" tab. */
    DIRECT,
    FACEBOOK,
    ZALO,
    SHOPEE,
    LAZADA,
    TIKTOK,
    OTHER
}

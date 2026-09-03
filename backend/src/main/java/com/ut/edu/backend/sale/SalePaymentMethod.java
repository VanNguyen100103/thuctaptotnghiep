package com.ut.edu.backend.sale;

/**
 * A single tender type on a POS sale's payment. Deliberately separate from
 * {@link com.ut.edu.backend.payment.PaymentMethod} (PAYPAL/MOMO/... for the
 * online storefront) - these are the in-person register options KiotViet's
 * "Bán hàng" screen offers, matching its 4 buttons exactly.
 */
public enum SalePaymentMethod {
    /** "Tiền mặt" */
    CASH,
    /** "Chuyển khoản" */
    BANK_TRANSFER,
    /** "Thẻ" */
    CARD,
    /** "Ví" */
    EWALLET
}

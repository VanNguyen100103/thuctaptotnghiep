package com.ut.edu.backend.salereturn;

/**
 * Whether the money on a {@link SaleReturn} has actually reached the
 * customer yet.
 *
 * Not a status of the return itself - the goods are back and the stock is
 * corrected the moment the receipt is written, and none of that is in doubt.
 * This is only about the refund leg, which for a bank transfer happens
 * outside the app entirely: SePay can watch the account but cannot send from
 * it, so the shop owner makes that transfer in their banking app.
 */
public enum SaleReturnRefundStatus {

    /**
     * "Chờ chuyển tiền" - the shop still owes this customer. Only ever the
     * opening state of a BANK_TRANSFER refund worth more than zero; cash,
     * card and wallet refunds change hands at the counter.
     */
    PENDING,

    /** "Đã hoàn tiền" - the customer has the money. */
    REFUNDED
}

package com.ut.edu.backend.tax;

/**
 * "Phương pháp tính thuế" - how a household business is assessed.
 *
 * Only {@link #KE_KHAI} produces the quarterly 01/CNKD this module builds.
 * KHOAN is here because the Thiết lập screen has to let a shop say which one
 * it is on - a khoán household owes a flat amount the tax office set in
 * advance and files nothing quarterly, so the declaration screens tell it so
 * instead of inventing numbers it must not send.
 */
public enum TaxMethod {
    /** Kê khai - declares its own revenue each period. Since 01/01/2026 every household business is on this. */
    KE_KHAI,
    /** Khoán - pays a fixed assessed amount; no periodic declaration. */
    KHOAN
}

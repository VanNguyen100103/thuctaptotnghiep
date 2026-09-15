package com.ut.edu.backend.tax;

/**
 * "Trạng thái tờ khai" as the 01/CNKD list shows it.
 *
 * Deliberately NOT a stored column. Two of the three are facts about the
 * calendar, and a stored copy would go stale the moment a quarter ended
 * without anybody opening the screen; the only thing the shop actually
 * decides is whether it has filed, which TaxDeclaration records as a
 * submittedAt timestamp. TaxDeclarationService derives this from the two.
 */
public enum TaxDeclarationStatus {
    /** Đang cập nhật - the period has not closed yet, so the figures still move with every sale. */
    DANG_CAP_NHAT,
    /** Chưa nộp - the period has closed and nobody has marked the return as filed. */
    CHUA_NOP,
    /** Đã nộp - the shop marked it filed; the figures are frozen at the snapshot taken then. */
    DA_NOP
}

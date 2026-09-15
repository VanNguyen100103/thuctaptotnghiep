package com.ut.edu.backend.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row of the 01/CNKD list - "Quý 1 / 2026 · 30/04/2026 · Lần đầu · Chưa
 * nộp · 709.651 · Xem chi tiết".
 *
 * dueDate is null while the period is still running, which is what makes the
 * list print "Chưa đến kỳ nộp tờ khai" there instead of a date: the deadline
 * exists on the calendar, but showing it beside an open quarter reads as a
 * bill that is already due.
 */
public record TaxDeclarationRowResponse(
        int year,
        TaxPeriodType periodType,
        int periodNumber,
        String periodLabel,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate dueDate,
        long overdueDays,
        TaxDeclarationStatus status,
        /** "Lần kê khai": 1 = Lần đầu, 2+ = Bổ sung lần N. */
        int declarationRound,
        LocalDateTime submittedAt,
        BigDecimal taxableRevenue,
        BigDecimal vatAmount,
        BigDecimal pitAmount,
        /** "Số tiền thuế" - the column the list totals, GTGT + TNCN. */
        BigDecimal totalTax) {
}

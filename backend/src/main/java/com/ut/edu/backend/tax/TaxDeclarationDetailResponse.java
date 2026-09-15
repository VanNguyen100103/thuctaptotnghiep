package com.ut.edu.backend.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * "Xem chi tiết" - one period's 01/CNKD as the form itself, plus the working
 * behind it.
 *
 * The working (revenue split by till, how many documents fed each side) is
 * not on the paper form and is the reason this screen exists rather than a
 * PDF: the first question a shop asks about a tax figure is where it came
 * from, and being able to answer "45.802.250 = 38 hóa đơn tại quầy + 4 đơn
 * online" is what makes the number trustworthy enough to file.
 */
public record TaxDeclarationDetailResponse(
        int year,
        TaxPeriodType periodType,
        int periodNumber,
        String periodLabel,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate dueDate,
        long overdueDays,
        TaxDeclarationStatus status,
        int declarationRound,
        LocalDateTime submittedAt,
        String note,

        // ---- the taxpayer, as the form's part A prints it ----
        TaxProfileResponse profile,

        // ---- section 2 of the form ----
        List<TaxActivityLineResponse> activityLines,
        BigDecimal taxableRevenue,
        BigDecimal vatAmount,
        BigDecimal pitAmount,
        BigDecimal totalTax,

        // ---- the working ----
        BigDecimal posRevenue,
        long posCount,
        BigDecimal onlineRevenue,
        long onlineCount,
        /** True once filed: the figures above are the snapshot that was sent, not a live total. */
        boolean frozen) {
}

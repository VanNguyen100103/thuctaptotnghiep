package com.ut.edu.backend.tax;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * One declaration period - "Quý 1/2026" or "Tháng 3/2026" - and everything
 * the 01/CNKD list needs to say about it: its own bounds, its filing
 * deadline, and whether it has closed.
 *
 * A record rather than rows in a table, because a period is arithmetic on a
 * calendar: Q3/2026 exists whether or not anybody has stored it, and the
 * store's own {@link TaxDeclaration} row is only created when there is
 * something to remember about it (that it was filed). Generating the list
 * from the calendar is also what makes a brand-new store's screen show the
 * right quarters on the first visit with no seeding at all.
 *
 * @param number 1-4 for quarters, 1-12 for months
 */
public record TaxPeriod(int year, TaxPeriodType type, int number) {

    /** "Quý 1", "Tháng 3" - the first line of the list's leftmost cell; the year sits under it. */
    public String label() {
        return type == TaxPeriodType.QUARTER ? "Quý " + number : "Tháng " + number;
    }

    public LocalDate startDate() {
        int startMonth = type == TaxPeriodType.QUARTER ? (number - 1) * 3 + 1 : number;
        return LocalDate.of(year, startMonth, 1);
    }

    public LocalDate endDate() {
        int endMonth = type == TaxPeriodType.QUARTER ? number * 3 : number;
        return YearMonth.of(year, endMonth).atEndOfMonth();
    }

    public LocalDateTime startAt() {
        return startDate().atStartOfDay();
    }

    /** Exclusive-feeling but inclusive bound: 23:59:59.999999999 of the last day, so a late-evening sale still lands in its own quarter. */
    public LocalDateTime endAt() {
        return endDate().atTime(LocalTime.MAX);
    }

    /**
     * "Hạn nộp tờ khai" - Điều 44, Luật Quản lý thuế 38/2019: the last day of
     * the first month of the following quarter for a quarterly filer, and the
     * 20th of the following month for a monthly one. Q4 therefore falls due
     * on 31/01 of the NEXT year, which is why this walks the month forward
     * rather than clamping inside {@code year}.
     */
    public LocalDate dueDate() {
        if (type == TaxPeriodType.QUARTER) {
            return YearMonth.from(endDate()).plusMonths(1).atEndOfMonth();
        }
        return YearMonth.from(endDate()).plusMonths(1).atDay(20);
    }

    public boolean hasStarted(LocalDate today) {
        return !today.isBefore(startDate());
    }

    /** A period is only declarable once it is over - before that the figures are still moving. */
    public boolean hasClosed(LocalDate today) {
        return today.isAfter(endDate());
    }

    /** Days past {@link #dueDate()}, or 0 while still in time. Drives the list's orange "Quá hạn nộp N ngày". */
    public long overdueDays(LocalDate today) {
        return today.isAfter(dueDate()) ? ChronoUnit.DAYS.between(dueDate(), today) : 0;
    }

    /**
     * The periods of {@code year} the list should show: every one that has
     * begun, so the current period appears as "Đang cập nhật" and future ones
     * stay off the screen entirely. A past year lists all of its periods.
     */
    public static List<TaxPeriod> ofYear(int year, TaxPeriodType type, LocalDate today) {
        int count = type == TaxPeriodType.QUARTER ? 4 : 12;
        List<TaxPeriod> periods = new ArrayList<>();
        for (int n = 1; n <= count; n++) {
            TaxPeriod period = new TaxPeriod(year, type, n);
            if (period.hasStarted(today)) {
                periods.add(period);
            }
        }
        return periods;
    }
}

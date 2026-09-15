package com.ut.edu.backend.tax;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The calendar rules the 01/CNKD list is built on. Worth their own test
 * because every one of them is a date a shop can be fined over, and three of
 * the four cross a boundary that is easy to get wrong: Q4's deadline falls in
 * the NEXT year, the current period must not offer a deadline at all, and the
 * list must stop at the period that has begun rather than showing four empty
 * quarters in January.
 */
class TaxPeriodTest {

    @Test
    void quarterBoundsCoverTheWholeQuarter() {
        TaxPeriod q1 = new TaxPeriod(2026, TaxPeriodType.QUARTER, 1);

        assertThat(q1.startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(q1.endDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        // A sale rung up at 23:50 on the last day belongs to this quarter.
        assertThat(q1.endAt().getHour()).isEqualTo(23);
        assertThat(q1.label()).isEqualTo("Quý 1");
    }

    @Test
    void februaryEndsOnTheRightDayInALeapYear() {
        assertThat(new TaxPeriod(2028, TaxPeriodType.MONTH, 2).endDate()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(new TaxPeriod(2026, TaxPeriodType.MONTH, 2).endDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void quarterlyDeadlineIsTheLastDayOfTheFirstMonthOfTheNextQuarter() {
        assertThat(new TaxPeriod(2026, TaxPeriodType.QUARTER, 1).dueDate()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(new TaxPeriod(2026, TaxPeriodType.QUARTER, 2).dueDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(new TaxPeriod(2026, TaxPeriodType.QUARTER, 3).dueDate()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void fourthQuarterIsDueInTheFollowingYear() {
        assertThat(new TaxPeriod(2026, TaxPeriodType.QUARTER, 4).dueDate()).isEqualTo(LocalDate.of(2027, 1, 31));
    }

    @Test
    void monthlyDeadlineIsTheTwentiethOfTheFollowingMonth() {
        assertThat(new TaxPeriod(2026, TaxPeriodType.MONTH, 3).dueDate()).isEqualTo(LocalDate.of(2026, 4, 20));
        assertThat(new TaxPeriod(2026, TaxPeriodType.MONTH, 12).dueDate()).isEqualTo(LocalDate.of(2027, 1, 20));
    }

    @Test
    void aPeriodOnlyClosesAfterItsLastDay() {
        TaxPeriod q1 = new TaxPeriod(2026, TaxPeriodType.QUARTER, 1);

        assertThat(q1.hasClosed(LocalDate.of(2026, 3, 31))).isFalse();
        assertThat(q1.hasClosed(LocalDate.of(2026, 4, 1))).isTrue();
    }

    @Test
    void overdueDaysCountFromTheDeadlineAndAreZeroWhileInTime() {
        TaxPeriod q1 = new TaxPeriod(2026, TaxPeriodType.QUARTER, 1);

        assertThat(q1.overdueDays(LocalDate.of(2026, 4, 30))).isZero();
        assertThat(q1.overdueDays(LocalDate.of(2026, 5, 3))).isEqualTo(3);
        // Across a month boundary: the arithmetic is in days, not calendar parts.
        assertThat(q1.overdueDays(LocalDate.of(2026, 6, 1))).isEqualTo(32);
    }

    @Test
    void theYearListsOnlyThePeriodsThatHaveBegun() {
        List<TaxPeriod> periods = TaxPeriod.ofYear(2026, TaxPeriodType.QUARTER, LocalDate.of(2026, 9, 15));

        assertThat(periods).extracting(TaxPeriod::number).containsExactly(1, 2, 3);
    }

    @Test
    void aPastYearListsEveryPeriod() {
        List<TaxPeriod> periods = TaxPeriod.ofYear(2025, TaxPeriodType.QUARTER, LocalDate.of(2026, 9, 15));

        assertThat(periods).extracting(TaxPeriod::number).containsExactly(1, 2, 3, 4);
    }

    @Test
    void aFutureYearListsNothing() {
        assertThat(TaxPeriod.ofYear(2027, TaxPeriodType.QUARTER, LocalDate.of(2026, 9, 15))).isEmpty();
    }
}

package com.ut.edu.backend.tax;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the 01/CNKD screen draws in one response: the four cumulative
 * cards across the top, the rows under them, and the years the picker should
 * offer.
 *
 * The two revenue cards carry the same figure today - one activity means one
 * revenue base for both taxes - and are still reported separately because the
 * form has two lines for them and they part company the moment a shop
 * declares under more than one activity group. Collapsing them into one field
 * now would be a schema change later for no gain.
 */
public record TaxYearSummaryResponse(
        int year,
        TaxPeriodType periodType,
        TaxMethod taxMethod,
        TaxActivity activity,
        String activityLabel,
        BigDecimal vatRate,
        BigDecimal pitRate,
        /** "Tổng doanh thu luỹ kế tính thuế GTGT" - every period of the year so far. */
        BigDecimal cumulativeVatRevenue,
        /** "Tổng tiền thuế GTGT luỹ kế phải nộp". */
        BigDecimal cumulativeVatAmount,
        BigDecimal cumulativePitRevenue,
        BigDecimal cumulativePitAmount,
        /** Below this the household owes neither tax for the year; 0 turns the notice off. See TaxProfile#exemptThreshold. */
        BigDecimal exemptThreshold,
        /** True while the year's cumulative revenue is still under the threshold - the screen says so rather than hiding the figures. */
        boolean underExemptThreshold,
        List<Integer> availableYears,
        List<TaxDeclarationRowResponse> periods) {
}

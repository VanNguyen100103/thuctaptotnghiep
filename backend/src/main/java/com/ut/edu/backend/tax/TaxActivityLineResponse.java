package com.ut.edu.backend.tax;

import java.math.BigDecimal;

/**
 * One of the four rows in section 2 of tờ khai 01/CNKD - an activity group,
 * the revenue declared under it, and the two taxes that fall out of its
 * rates.
 *
 * All four are always returned, zeros included, because the paper form has
 * all four and a shop checking its return against the PDF needs the lines to
 * sit where the PDF puts them.
 */
public record TaxActivityLineResponse(
        TaxActivity activity,
        String label,
        BigDecimal revenue,
        BigDecimal vatRate,
        BigDecimal vatAmount,
        BigDecimal pitRate,
        BigDecimal pitAmount) {
}

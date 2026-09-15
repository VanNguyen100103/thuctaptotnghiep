package com.ut.edu.backend.tax;

/**
 * "Kỳ kê khai" - how often 01/CNKD is filed. Quarterly is the default for a
 * household business; monthly is what a shop over 50 tỷ of annual revenue
 * opts into, and the form itself is identical either way - only the period
 * boundaries and the deadline rule change (see TaxPeriod).
 */
public enum TaxPeriodType {
    QUARTER,
    MONTH
}

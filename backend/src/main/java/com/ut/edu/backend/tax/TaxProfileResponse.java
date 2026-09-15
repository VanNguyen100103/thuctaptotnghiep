package com.ut.edu.backend.tax;

import java.math.BigDecimal;

/**
 * The Thiết lập screen's whole state, plus the rate pair its chosen activity
 * implies. The rates ride along read-only so the screen can show "GTGT 1% /
 * TNCN 0,5%" beside the picker without the frontend keeping a second copy of
 * the law.
 */
public record TaxProfileResponse(
        String businessName,
        String taxCode,
        String ownerName,
        String businessAddress,
        String taxOffice,
        TaxMethod taxMethod,
        TaxPeriodType periodType,
        TaxActivity activity,
        String activityLabel,
        BigDecimal vatRate,
        BigDecimal pitRate,
        BigDecimal exemptThreshold) {

    public static TaxProfileResponse of(TaxProfile profile) {
        TaxActivity activity = profile.getActivity();
        return new TaxProfileResponse(
                profile.getBusinessName(),
                profile.getTaxCode(),
                profile.getOwnerName(),
                profile.getBusinessAddress(),
                profile.getTaxOffice(),
                profile.getTaxMethod(),
                profile.getPeriodType(),
                activity,
                activity.getLabel(),
                activity.getVatRate(),
                activity.getPitRate(),
                profile.getExemptThreshold());
    }
}

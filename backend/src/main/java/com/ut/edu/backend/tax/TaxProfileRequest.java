package com.ut.edu.backend.tax;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of PUT /store/tax/profile - the Thiết lập form.
 *
 * Every field is optional: a shop opens this screen before it knows its MST
 * and must be able to save what it does know. The one thing checked is the
 * shape of the MST when it is given, because a malformed one is a rejected
 * filing and the shop would rather hear it here.
 */
public record TaxProfileRequest(
        @Size(max = 200) String businessName,
        @Pattern(regexp = "^$|^[0-9]{10}(-[0-9]{3})?$",
                 message = "Mã số thuế phải gồm 10 chữ số, hoặc 10 chữ số kèm đuôi 3 chữ số (vd 0123456789-001)")
        String taxCode,
        @Size(max = 200) String ownerName,
        @Size(max = 255) String businessAddress,
        @Size(max = 200) String taxOffice,
        TaxMethod taxMethod,
        TaxPeriodType periodType,
        TaxActivity activity,
        @DecimalMin(value = "0.0", message = "Ngưỡng doanh thu không chịu thuế không được âm")
        BigDecimal exemptThreshold) {
}

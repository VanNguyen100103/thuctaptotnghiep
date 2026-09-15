package com.ut.edu.backend.tax;

import java.math.BigDecimal;

/**
 * "Ngành nghề kinh doanh" as tờ khai 01/CNKD groups them - the four rows of
 * the form's section 2, each with its own pair of rates.
 *
 * The rates are the ones fixed by Phụ lục I, Thông tư 40/2021/TT-BTC: a
 * household business on the kê khai method pays a percentage OF REVENUE, not
 * of profit, so there is no cost side to model here at all. That is why this
 * module can produce a filing from invoices alone.
 *
 * Kept as an enum with the rates baked in rather than as configurable
 * numbers: a shop that could type its own rate would file a wrong return and
 * only find out when the tax office did. If the law changes the rates, that
 * is a code change with a migration to restate past filings - which is what
 * the snapshot columns on TaxDeclaration exist for.
 */
public enum TaxActivity {

    /** Phân phối, cung cấp hàng hóa - the retail shop's own row, and the default. */
    DISTRIBUTION("Phân phối, cung cấp hàng hóa", "1", "0.5"),

    /** Dịch vụ, xây dựng không bao thầu nguyên vật liệu - salons, repairs, consulting. */
    SERVICE("Dịch vụ, xây dựng không bao thầu nguyên vật liệu", "5", "2"),

    /** Sản xuất, vận tải, dịch vụ có gắn với hàng hóa, xây dựng có bao thầu nguyên vật liệu. */
    PRODUCTION("Sản xuất, vận tải, dịch vụ có gắn với hàng hóa, xây dựng có bao thầu NVL", "3", "1.5"),

    /** Hoạt động kinh doanh khác - the form's catch-all row. */
    OTHER("Hoạt động kinh doanh khác", "2", "1");

    private final String label;
    private final BigDecimal vatRate;
    private final BigDecimal pitRate;

    TaxActivity(String label, String vatRate, String pitRate) {
        this.label = label;
        this.vatRate = new BigDecimal(vatRate);
        this.pitRate = new BigDecimal(pitRate);
    }

    public String getLabel() {
        return label;
    }

    /** "Tỷ lệ thuế GTGT" in percent, e.g. 1 for the distribution row. */
    public BigDecimal getVatRate() {
        return vatRate;
    }

    /** "Tỷ lệ thuế TNCN" in percent. */
    public BigDecimal getPitRate() {
        return pitRate;
    }
}

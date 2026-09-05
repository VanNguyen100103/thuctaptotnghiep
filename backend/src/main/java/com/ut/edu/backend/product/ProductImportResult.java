package com.ut.edu.backend.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response for AdminProductController#importProducts. Rows are processed
 * sequentially and each successful row commits on its own - not one
 * all-or-nothing transaction like createProductVariants - because a spreadsheet
 * import stopping partway through (KiotViet's own "Báo lỗi và dừng import"
 * wording) is expected to keep whatever already imported cleanly, not discard
 * it. stoppedAtRow/stopReason are set only when a duplicate-name/duplicate-sku
 * conflict (or the store's plan product limit) halted processing before the
 * end of the file; notes are informational, non-blocking issues (a blank
 * required field, an unmatched category name) that just skip/soften one row.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductImportResult {

    private int totalRows;
    private int createdCount;
    private int updatedCount;
    private Integer stoppedAtRow;
    private String stopReason;
    private List<RowNote> notes = new ArrayList<>();

    public void addNote(int row, String message) {
        notes.add(new RowNote(row, message));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowNote {
        private int row;
        private String message;
    }
}

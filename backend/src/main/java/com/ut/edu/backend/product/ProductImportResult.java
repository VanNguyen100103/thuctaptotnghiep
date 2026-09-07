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
    /**
     * How many picture links the sheet carried, handed to
     * ProductImageImportService to fetch into Cloudinary after this response
     * is returned. Non-zero tells the import dialog to start polling
     * GET /import/images/progress instead of declaring the import finished.
     */
    private int queuedImageCount;
    private Integer stoppedAtRow;
    private String stopReason;
    private List<RowNote> notes = new ArrayList<>();
    /**
     * Rows that produced a note beyond {@link #MAX_NOTES}. A real export can
     * skip thousands of rows for the same reason (an unmatched "Mã ĐVT Cơ
     * bản" on every line, say); listing them all would make this response
     * megabytes of near-identical text, so the rest are only counted.
     */
    private int suppressedNoteCount;

    /** Enough notes to diagnose any pattern in the file, few enough to stay a small JSON response. */
    private static final int MAX_NOTES = 500;

    public void addNote(int row, String message) {
        if (notes.size() < MAX_NOTES) {
            notes.add(new RowNote(row, message));
        } else {
            suppressedNoteCount++;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowNote {
        private int row;
        private String message;
    }
}

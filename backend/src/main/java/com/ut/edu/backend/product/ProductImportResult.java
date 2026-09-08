package com.ut.edu.backend.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Live state of one store's spreadsheet import - returned by
 * AdminProductController#importProducts and then polled through
 * #importProgress until {@link #running} goes false.
 *
 * Rows are processed sequentially and each successful row commits on its own -
 * not one all-or-nothing transaction like createProductVariants - because a
 * spreadsheet import stopping partway through (KiotViet's own "Báo lỗi và dừng
 * import" wording) is expected to keep whatever already imported cleanly, not
 * discard it. stoppedAtRow/stopReason are set only when a
 * duplicate-name/duplicate-sku conflict (or the store's plan product limit)
 * halted processing before the end of the file; notes are informational,
 * non-blocking issues (a blank required field, an unmatched category name)
 * that just skip or soften one row.
 *
 * One instance is written by the import thread while the dashboard reads it
 * from request threads, so the counters are volatile and the notes list is
 * synchronized. Readers get a {@link #snapshot()} rather than this object:
 * serializing the live one could catch the notes list mid-append.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductImportResult {

    /**
     * True from the moment the upload is accepted until the sheet has been
     * read to its end (or stopped). The import dialog polls while it is true
     * instead of declaring the import finished - a real 14000-row export
     * takes far longer than any HTTP request may.
     */
    private volatile boolean running;

    private volatile int totalRows;
    private volatile int createdCount;
    private volatile int updatedCount;
    /**
     * How many picture links the sheet carried, handed to
     * ProductImageImportService to fetch into Cloudinary once the rows are
     * written. Non-zero tells the dialog to keep polling
     * GET /import/images/progress after the rows themselves are done.
     */
    private volatile int queuedImageCount;
    private volatile Integer stoppedAtRow;
    private volatile String stopReason;
    private List<RowNote> notes = Collections.synchronizedList(new ArrayList<>());
    /**
     * Rows that produced a note beyond {@link #MAX_NOTES}. A real export can
     * skip thousands of rows for the same reason (an unmatched "Mã ĐVT Cơ
     * bản" on every line, say); listing them all would make this response
     * megabytes of near-identical text, so the rest are only counted.
     */
    private volatile int suppressedNoteCount;

    /** Enough notes to diagnose any pattern in the file, few enough to stay a small JSON response. */
    private static final int MAX_NOTES = 500;

    public void addNote(int row, String message) {
        synchronized (notes) {
            if (notes.size() < MAX_NOTES) {
                notes.add(new RowNote(row, message));
                return;
            }
        }
        suppressedNoteCount++;
    }

    /** What the progress endpoint reports when this store has never started an import in this process. */
    public static ProductImportResult idle() {
        return new ProductImportResult();
    }

    /** A consistent copy to serialize, taken while the import thread may still be appending to this one. */
    public ProductImportResult snapshot() {
        ProductImportResult copy = new ProductImportResult();
        copy.running = running;
        copy.totalRows = totalRows;
        copy.createdCount = createdCount;
        copy.updatedCount = updatedCount;
        copy.queuedImageCount = queuedImageCount;
        copy.stoppedAtRow = stoppedAtRow;
        copy.stopReason = stopReason;
        copy.suppressedNoteCount = suppressedNoteCount;
        synchronized (notes) {
            copy.notes = new ArrayList<>(notes);
        }
        return copy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowNote {
        private int row;
        private String message;
    }
}

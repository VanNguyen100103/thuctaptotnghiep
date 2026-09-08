package com.ut.edu.backend.product;

import com.ut.edu.backend.category.Category;
import com.ut.edu.backend.category.CategoryRepository;
import com.ut.edu.backend.common.SlugUtil;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;

import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Bulk product import from an .xlsx file, matching KiotViet's own "Nhập hàng
 * hóa từ file dữ liệu" template column-for-column (headers, order, and
 * styling - a filled/bordered header row with filter dropdowns, like
 * KiotViet's own export). Template layout, as generateTemplate() writes it:
 * 0 Loại hàng | 1 Nhóm hàng(3 Cấp) | 2 Mã hàng | 3 Mã vạch | 4 Tên hàng |
 * 5 Thương hiệu | 6 Giá bán | 7 Giá vốn | 8 Tồn kho | 9 Tồn nhỏ nhất |
 * 10 Tồn lớn nhất | 11 ĐVT | 12 Mã ĐVT Cơ bản | 13 Quy đổi | 14 Mô tả |
 * 15 Hình ảnh (url1,url2...).
 *
 * Those positions are the fallback, not the contract: which column holds
 * what is resolved from the uploaded sheet's OWN header row (see
 * {@link ProductImportColumns}), so a real KiotViet "DanhSachSanPham"
 * export - ~26 columns in a different order - imports just as correctly as
 * this template does.
 *
 * The "Hình ảnh" column's links are not stored as text: each is fetched
 * into this store's Cloudinary account and recorded as a ProductImage row,
 * in the background, by {@link ProductImageImportService}.
 * Mã hàng/Tên hàng/Giá bán are required at import time even though the
 * header text no longer marks them with "*" (KiotViet's own template
 * doesn't either) - see the import dialog's info tooltip instead.
 *
 * "Quy đổi" is read but never persisted: this app doesn't store a unit
 * conversion factor anywhere, even for units created via the manual product
 * form's "Thiết lập đơn vị tính" builder (see UnitDef in
 * variant-builder.models.ts) - it's only ever used transiently there to seed
 * a generated row's price, and the import sheet already carries an explicit
 * "Giá bán"/"Giá vốn" per row so there's nothing left to derive from it.
 *
 * "ĐVT" and "Mã ĐVT Cơ bản" instead reuse the same machinery as that manual
 * builder: ĐVT is stored as the free-named "Đơn vị tính" entry in a product's
 * `attributes` map (no schema change needed), and a non-blank "Mã ĐVT Cơ bản"
 * links this row to another row's SKU by sharing one `variantGroupId` - the
 * same grouping AdminProductController#createProductVariants uses for
 * Color x Size siblings.
 *
 * Rows are processed sequentially and each successful row is saved on its
 * own - this is deliberately NOT one all-or-nothing transaction like
 * createProductVariants. KiotViet's own option wording ("Báo lỗi và dừng
 * import") describes halting partway through a spreadsheet, not discarding
 * rows already imported before the conflict - that matches how a bulk
 * import is normally used (fix the offending row, re-import the rest).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductImportService {

    private static final int MAX_ROWS = 50000;
    private static final int MAX_CATEGORY_DEPTH = 3;
    private static final String CATEGORY_PATH_SEPARATOR = ">>";
    private static final String UNIT_ATTRIBUTE_NAME = "Đơn vị tính";
    private static final String DEFAULT_PRODUCT_TYPE = "Hàng hóa";

    private static final String[] HEADERS = {
            "Loại hàng", "Nhóm hàng(3 Cấp)", "Mã hàng", "Mã vạch", "Tên hàng", "Thương hiệu",
            "Giá bán", "Giá vốn", "Tồn kho", "Tồn nhỏ nhất", "Tồn lớn nhất", "ĐVT",
            "Mã ĐVT Cơ bản", "Quy đổi", "Mô tả", "Hình ảnh (url1,url2...)",
    };

    /**
     * How many columns of a row are kept while parsing. Not HEADERS.length:
     * a real KiotViet export is wider than this app's own template (its
     * image column alone sits at Y/24), and anything past this is a column
     * ProductImportColumns has no field for anyway.
     */
    private static final int MAX_COLUMNS = 64;

    /** Same per-product ceiling CloudinaryService#validateImages applies to manual uploads. */
    private static final int MAX_IMAGES_PER_PRODUCT = 10;

    /** Column indices whose example-row value is numeric (right-aligned, thousands-separated) rather than free text. */
    private static final Set<Integer> NUMERIC_COLUMNS = Set.of(6, 7, 8, 9, 10, 13);

    /**
     * Example rows matching KiotViet's own "MauFileSanPham" sample data
     * column-for-column - a realistic, varied dataset (multiple product
     * types, a 3-level Dịch vụ category path, and a base/derived unit pair
     * linked via "Mã ĐVT Cơ bản") rather than one placeholder row, so a
     * first-time importer can see every column's intent at a glance.
     */
    private static final String[][] EXAMPLE_ROWS = {
            {"Hàng hóa", "Kẹo bánh", "HH000026", "364332862", "Kẹo Doublemint", "Doublemint", "10000", "8000", "5", "0", "50", "Hộp", "", "1", "", "https://res.cloudinary.com/demo/image/upload/sample.jpg"},
            {"Hàng hóa", "Kẹo bánh", "HH000025", "695588910", "Kẹo cao su tổng hợp", "", "10000", "8000", "5", "0", "50", "Hộp", "", "1", "", ""},
            {"Hàng hóa", "Mỹ phẩm", "HH000023", "824804043", "Sữa tắm Palmolive xanh lá", "Colgate", "10000", "8000", "10", "0", "50", "Lọ", "", "1", "", ""},
            {"Hàng hóa", "Mỹ phẩm", "HH000016", "720467868", "Kem dưỡng da Johnson xanh", "Johnson & Johnson", "3000", "1000", "10", "0", "50", "Lọ", "", "1", "", ""},
            {"Hàng hóa", "Mỹ phẩm", "HH000015", "421176476", "Kem dưỡng da Johnson xanh", "Johnson & Johnson", "30000", "10000", "5", "0", "50", "Thùng", "HH000016", "10", "", ""},
            {"Hàng hóa", "Thực phẩm", "HH000011", "284018188", "Phở bò phở cổ", "", "39000", "25000", "15", "0", "50", "Gói", "", "1", "", ""},
            {"Hàng hóa", "Thực phẩm", "HH000009", "441382011", "Thịt bò khô 30g", "", "60000", "48000", "5", "0", "50", "Gói", "", "1", "", ""},
            {"Dịch vụ", "Dịch vụ>>Gói quà", "HH000008", "297019677", "Gói quà", "", "180000", "180000", "0", "0", "0", "", "", "", "", ""},
            {"Dịch vụ", "Dịch vụ>>Rửa xe", "HH000099", "360601057", "Rửa xe", "", "350000", "300000", "0", "0", "100", "", "", "", "", ""},
            {"Combo", "Mỹ phẩm", "HH000010", "622840957", "Set mỹ phẩm tổng hợp", "", "200000", "142000", "5", "0", "50", "Set", "", "1", "", ""},
    };

    /**
     * How many rows to process between EntityManager flush+clear cycles.
     * Spring's spring.jpa.open-in-view (on by default, unset in any profile
     * here) keeps ONE Hibernate persistence context open for the entire
     * HTTP request regardless of how many separate repository-method
     * transactions run within it - so without this, a multi-thousand-row
     * import would accumulate every Product it touches (each with several
     * eager @ElementCollection fields) as managed entities for the whole
     * request, which is the same "load everything into memory at once"
     * failure mode as the old Excel-parsing code, just on the Hibernate
     * side instead of the POI side. Clearing periodically bounds that to
     * roughly one batch's worth of entities at a time.
     */
    private static final int FLUSH_INTERVAL = 200;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final TenantGuard tenantGuard;
    private final SubscriptionGuard subscriptionGuard;
    private final ProductImageImportService productImageImportService;
    private final RedisProductCacheService productCacheService;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * One import at a time, process-wide. Deliberately single-threaded: a
     * sheet already costs several database round trips per row, and running
     * two of them at once would only fight over the same connection pool -
     * a second upload waits (or is rejected while one is still running).
     */
    private final ExecutorService importPool = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "product-import");
        thread.setDaemon(true);
        return thread;
    });

    /** Latest import per store, kept so the dialog can poll it after the upload request has returned. */
    private final Map<Long, ProductImportResult> jobsByStore = new ConcurrentHashMap<>();

    @PreDestroy
    void shutdown() {
        importPool.shutdownNow();
    }

    public byte[] generateTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Hàng hóa");

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            XSSFCellStyle headerStyle = (XSSFCellStyle) workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xDC, (byte) 0xE6, (byte) 0xF1}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 20 * 256);
            }
            // Filter dropdown arrows on the header row, matching KiotViet's own export.
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            XSSFCellStyle numberStyle = (XSSFCellStyle) workbook.createCellStyle();
            numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));

            for (int r = 0; r < EXAMPLE_ROWS.length; r++) {
                Row row = sheet.createRow(r + 1);
                String[] values = EXAMPLE_ROWS[r];
                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.createCell(i);
                    String value = values[i];
                    if (NUMERIC_COLUMNS.contains(i) && !value.isBlank()) {
                        cell.setCellValue(Double.parseDouble(value));
                        cell.setCellStyle(numberStyle);
                    } else {
                        cell.setCellValue(value);
                    }
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate import template", e);
        }
    }

    /**
     * Streams the uploaded sheet row-by-row via POI's SAX ("event") API
     * (XSSFReader + a raw &lt;sheetData&gt; handler) instead of the
     * DOM-based WorkbookFactory/XSSFWorkbook "usermodel" API this class
     * used before. Usermodel parses every row/cell/style of the ENTIRE
     * sheet into Java objects up front - regardless of MAX_ROWS, which only
     * ever limited how many of those already-parsed rows got processed - so
     * a real multi-thousand-row export blew the 192MB heap on Render's free
     * tier ("Terminating due to java.lang.OutOfMemoryError: Java heap
     * space") before the row loop even started. This handler holds only the
     * current row's cells in memory at any given time.
     *
     * Cell values are read from each cell's raw &lt;v&gt; text (resolving
     * shared-string indices via the workbook's SharedStrings table) rather
     * than a formatted display string: a formatted string would follow
     * whatever number format the source file's author applied to that cell
     * (real-world exports of this data use Vietnamese locale formatting
     * like "105.000,0"), which is ambiguous to re-parse. The raw value is
     * locale-independent, matching what Cell#getNumericCellValue() returned
     * under the old usermodel code.
     */
    /**
     * Request-thread entry point: resolves the tenant, parks the upload in a
     * temp file and hands the parse to {@link #importPool}, returning at once
     * with a result whose {@code running} flag is still true.
     *
     * The parse cannot stay on the request thread. A real export is 14000+
     * rows and every row costs several round trips to a (free-tier, remote)
     * Postgres, which runs to tens of minutes - far past any browser, proxy
     * or platform request timeout. It used to be capped at 5000 rows to stay
     * inside one request and even that did not fit.
     */
    public ProductImportResult startImport(MultipartFile file, ProductImportOptions options) {
        Long storeId = tenantGuard.requireStore();
        Store storeRef = tenantGuard.currentStoreRef();

        ProductImportResult inFlight = jobsByStore.get(storeId);
        if (inFlight != null && inFlight.isRunning()) {
            throw new IllegalStateException(
                    "Cửa hàng đang có một lần nhập file chạy dở (%d dòng đã xử lý) - đợi xong rồi nhập tiếp."
                            .formatted(inFlight.getTotalRows()));
        }

        File tempFile = writeToTempFile(file);
        ProductImportResult result = new ProductImportResult();
        result.setRunning(true);
        jobsByStore.put(storeId, result);
        importPool.submit(() -> runImport(tempFile, storeId, storeRef, options, result));
        return result;
    }

    /** Live state of this store's import, for the dialog to poll. */
    public ProductImportResult progressFor(Long storeId) {
        ProductImportResult result = jobsByStore.get(storeId);
        return result == null ? ProductImportResult.idle() : result.snapshot();
    }

    /**
     * Blocking variant - parses and returns only once the whole sheet is
     * done. Kept for callers that can wait (and for the tests, which assert
     * on a finished result); {@link #startImport} is what the dashboard uses.
     */
    public ProductImportResult importFromExcel(MultipartFile file, ProductImportOptions options) {
        Long storeId = tenantGuard.requireStore();
        Store storeRef = tenantGuard.currentStoreRef();
        ProductImportResult result = new ProductImportResult();
        result.setRunning(true);
        runImport(writeToTempFile(file), storeId, storeRef, options, result);
        return result;
    }

    /**
     * Written to a real file (rather than parsed straight off the multipart
     * InputStream) so OPCPackage can open it with true random-file-access
     * reads - an InputStream-backed OPCPackage has to buffer the whole zip
     * into memory first, since ZIP central-directory lookups need seekable
     * access. It also has to outlive the request: the parse runs later, on
     * another thread, long after the multipart temp storage is recycled.
     */
    private File writeToTempFile(MultipartFile file) {
        try {
            File tempFile = File.createTempFile("product-import-", ".xlsx");
            file.transferTo(tempFile);
            return tempFile;
        } catch (IOException e) {
            throw new IllegalArgumentException("Không đọc được file - vui lòng dùng đúng file mẫu .xlsx", e);
        }
    }

    private void runImport(File tempFile, Long storeId, Store storeRef, ProductImportOptions options,
                           ProductImportResult result) {
        ImportState state = new ImportState(storeId, storeRef, productRepository.countByStoreId(storeId), options, result);
        try {
            try (OPCPackage pkg = OPCPackage.open(tempFile, PackageAccess.READ)) {
                XSSFReader reader = new XSSFReader(pkg);
                SharedStrings sharedStrings = reader.getSharedStringsTable();
                XMLReader xmlReader = XMLHelper.newXMLReader();
                xmlReader.setContentHandler(new RawSheetHandler(sharedStrings, state));

                Iterator<InputStream> sheets = reader.getSheetsData();
                if (sheets.hasNext()) {
                    try (InputStream sheetStream = sheets.next()) {
                        xmlReader.parse(new InputSource(sheetStream));
                    }
                }
            } catch (StopImportException stop) {
                // Expected early exit: MAX_ROWS reached, or a row triggered
                // a hard stop (duplicate conflict, subscription limit) -
                // result.stoppedAtRow/stopReason is already set by then.
            }
        } catch (IOException | OpenXML4JException | SAXException | ParserConfigurationException e) {
            // Reached only for a file that isn't a readable .xlsx. Nothing
            // may escape this method: on the pool thread there is no caller
            // left to catch it, and the dialog would poll a job that never
            // ends.
            log.warn("Product import for store {} could not read the uploaded file", storeId, e);
            result.setStopReason("Không đọc được file - vui lòng dùng đúng file mẫu .xlsx");
        } catch (RuntimeException e) {
            log.error("Product import for store {} failed", storeId, e);
            result.setStopReason("Nhập file thất bại: " + rootMessage(e));
        } finally {
            if (!tempFile.delete()) {
                log.warn("Failed to delete temp import file {}", tempFile);
            }
            try {
                linkUnitVariants(state.pendingUnitLinks, result, storeId);
                // Pictures are fetched after the rows are safely written, on
                // their own pool - see ProductImageImportService.
                result.setQueuedImageCount(productImageImportService.enqueue(storeId, state.pendingImages));
                // Imported rows are invisible to admin search, the storefront
                // and the AI chat's search_products tool for up to 15 minutes
                // otherwise - the request that started this import returned
                // long before there was anything to invalidate.
                productCacheService.invalidateAllSearchResults();
            } catch (RuntimeException e) {
                log.error("Post-import steps failed for store {}", storeId, e);
            } finally {
                result.setRunning(false);
            }
        }

        log.info("Product import for store {}: {} created, {} updated, {} total rows, {} image(s) queued{}",
                storeId, result.getCreatedCount(), result.getUpdatedCount(), result.getTotalRows(),
                result.getQueuedImageCount(),
                result.getStoppedAtRow() != null ? ", stopped at row " + result.getStoppedAtRow() : "");
    }

    /** Thrown purely as control flow to unwind the SAX parse early; caught around xmlReader.parse(). */
    private static final class StopImportException extends RuntimeException {
        StopImportException() {
            super(null, null, false, false); // no message/stack trace needed
        }
    }

    /** Mutable state threaded through row processing while the SAX parse is in progress (replaces importFromExcel's old local loop variables). */
    private static final class ImportState {
        final Long storeId;
        final Store storeRef;
        long currentProductCount;
        final ProductImportOptions options;
        final ProductImportResult result;
        final Set<String> usedSlugsInBatch = new HashSet<>();
        final Map<String, Category> categoryPathCache = new HashMap<>();
        final List<PendingUnitLink> pendingUnitLinks = new ArrayList<>();
        /** Image URLs to fetch into Cloudinary once the whole sheet is read - only ids and URLs, so a 1200-row sheet costs a few hundred KB here. */
        final List<ProductImageImportService.PendingProductImages> pendingImages = new ArrayList<>();
        /** Replaced by the header row's own mapping when the sheet names its columns; see ProductImportColumns. */
        ProductImportColumns columns = ProductImportColumns.fixedLayout();
        int unflushedRows;

        ImportState(Long storeId, Store storeRef, long currentProductCount, ProductImportOptions options, ProductImportResult result) {
            this.storeId = storeId;
            this.storeRef = storeRef;
            this.currentProductCount = currentProductCount;
            this.options = options;
            this.result = result;
        }
    }

    /**
     * SAX handler reading a sheet's raw &lt;sheetData&gt; XML directly
     * (rather than via XSSFSheetXMLHandler's formatted-value layer - see
     * importFromExcel's javadoc for why): buffers only the current row's
     * cell values, resolves shared-string cells against the workbook's
     * SharedStrings table, and hands each completed data row to
     * processDataRow.
     */
    private final class RawSheetHandler extends DefaultHandler {
        private final SharedStrings sharedStrings;
        private final ImportState state;
        private final StringBuilder value = new StringBuilder();

        private String[] currentRow;
        private int currentRowNum = -1;
        private int currentCol = -1;
        private String currentCellType;
        private boolean captureValue;

        RawSheetHandler(SharedStrings sharedStrings, ImportState state) {
            this.sharedStrings = sharedStrings;
            this.state = state;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            switch (qName) {
                case "row" -> {
                    currentRowNum = parseRowNum(attributes.getValue("r"));
                    currentCol = -1;
                    currentRow = new String[MAX_COLUMNS];
                    Arrays.fill(currentRow, "");
                }
                case "c" -> {
                    String ref = attributes.getValue("r");
                    currentCol = ref != null ? new CellReference(ref).getCol() : currentCol + 1;
                    currentCellType = attributes.getValue("t");
                }
                case "v", "t" -> {
                    captureValue = true;
                    value.setLength(0);
                }
                default -> {
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (captureValue) {
                value.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            switch (qName) {
                case "v", "t" -> {
                    captureValue = false;
                    if (currentCol >= 0 && currentCol < currentRow.length) {
                        currentRow[currentCol] = resolveValue(value.toString(), currentCellType);
                    }
                }
                case "row" -> {
                    if (currentRowNum == 0) {
                        // The header row is data too: it says which column is
                        // which, so a KiotViet export's own layout imports as
                        // correctly as this app's template.
                        state.columns = ProductImportColumns.fromHeaderRow(currentRow);
                        log.info("Product import column mapping: {}", state.columns.describe());
                    } else if (currentRowNum >= 1) {
                        processDataRow(currentRow, currentRowNum, state);
                    }
                    if (currentRowNum >= MAX_ROWS) {
                        // A safety net now, not a working limit: the parse no
                        // longer has to fit inside one HTTP request, so a real
                        // 14000-row export runs to its end. Whatever stops
                        // here still says so - silently importing two thirds
                        // of a file and reporting success is worse than a
                        // refusal.
                        if (state.result.getStopReason() == null) {
                            state.result.setStoppedAtRow(currentRowNum + 1);
                            state.result.setStopReason(
                                    "File có nhiều hơn %d dòng - mới nhập tới dòng %d, phần còn lại chưa được nhập. Hãy tách file rồi nhập tiếp."
                                            .formatted(MAX_ROWS, currentRowNum));
                        }
                        throw new StopImportException();
                    }
                }
                default -> {
                }
            }
        }

        private int parseRowNum(String rAttr) {
            if (rAttr == null) {
                return currentRowNum + 1;
            }
            try {
                return Integer.parseInt(rAttr) - 1; // spreadsheet rows are 1-based; POI's are 0-based
            } catch (NumberFormatException e) {
                return currentRowNum + 1;
            }
        }

        private String resolveValue(String raw, String type) {
            if (raw.isBlank()) {
                return "";
            }
            if ("s".equals(type)) {
                try {
                    return sharedStrings.getItemAt(Integer.parseInt(raw)).getString();
                } catch (NumberFormatException e) {
                    return "";
                }
            }
            if ("str".equals(type) || "inlineStr".equals(type) || "b".equals(type) || "e".equals(type)) {
                return raw;
            }
            // Numeric cell (t absent or "n"): render the same way the old
            // usermodel code did via Cell#getNumericCellValue(), so
            // downstream parsing (cellDecimal/cellInt) is unaffected.
            try {
                double v = Double.parseDouble(raw);
                return v == Math.floor(v) && !Double.isInfinite(v) ? String.valueOf((long) v) : String.valueOf(v);
            } catch (NumberFormatException e) {
                return raw;
            }
        }
    }

    /**
     * Runs one row, turning anything it throws into a skipped-row note.
     *
     * Without this, a single row the database rejects - a name past its
     * column length, a stock figure a @Min(0) rejects, a category whose
     * generated slug collides - aborted the whole upload with a blanket
     * "Failed to import products" 500 and no clue which row was at fault.
     * On a real 5000-row export that is the difference between "4996 dòng
     * đã nhập, 4 dòng bỏ qua vì..." and nothing at all.
     *
     * The persistence context is cleared afterwards: the failed row's own
     * transaction has rolled back, but under OSIV the half-populated entity
     * would otherwise stay managed in the shared session and be re-flushed
     * with the next row, failing it too.
     */
    private void processDataRow(String[] cells, int rowIndex, ImportState state) {
        try {
            importDataRow(cells, rowIndex, state);
        } catch (StopImportException stop) {
            throw stop; // deliberate halt (duplicate conflict, plan limit, MAX_ROWS)
        } catch (RuntimeException e) {
            log.warn("Import row {} failed", rowIndex + 1, e);
            state.result.addNote(rowIndex + 1, "Bỏ qua: " + rootMessage(e));
            entityManager.clear();
        }
    }

    /** Deepest cause's message - a JPA failure wraps the useful text (e.g. Postgres' own "value too long for type ...") several layers down. */
    private String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            message = root.getClass().getSimpleName();
        }
        message = message.replace('\n', ' ').trim();
        return message.length() > 300 ? message.substring(0, 300) + "..." : message;
    }

    /** One data row's worth of the old importFromExcel loop body, ported to read from a raw String[] instead of a POI Row. */
    private void importDataRow(String[] cells, int rowIndex, ImportState state) {
        ProductImportColumns columns = state.columns;
        if (isBlankRow(cells, columns)) {
            return;
        }
        ProductImportResult result = state.result;
        ProductImportOptions options = state.options;
        result.setTotalRows(result.getTotalRows() + 1);
        int displayRow = rowIndex + 1; // 1-based spreadsheet row number for messages

        String productType = cellStr(cells, columns.productType());
        String categoryPath = cellStr(cells, columns.categoryPath());
        String sku = cellStr(cells, columns.sku());
        String barcode = cellStr(cells, columns.barcode());
        String name = cellStr(cells, columns.name());
        String brand = cellStr(cells, columns.brand());
        BigDecimal price = cellDecimal(cells, columns.price());
        BigDecimal costPrice = cellDecimal(cells, columns.costPrice());
        Integer stockQuantity = cellInt(cells, columns.stock());
        Integer minStockThreshold = cellInt(cells, columns.minStock());
        Integer maxStockThreshold = cellInt(cells, columns.maxStock());
        String unitName = cellStr(cells, columns.unit());
        // A real KiotViet export can write a literal "0" into this numeric-
        // looking column for an ordinary single-unit row instead of leaving
        // it blank (seen in production testing). No real product is ever
        // coded "0" (this template's own sample SKUs all look like
        // "HH000016"), so treat "0" the same as blank rather than reporting
        // a "Mã ĐVT Cơ bản not found" note on nearly every row.
        String baseUnitSku = "0".equals(cellStr(cells, columns.baseUnitSku())) ? "" : cellStr(cells, columns.baseUnitSku());
        // "Quy đổi" is intentionally unread - see class javadoc.
        String description = cellStr(cells, columns.description());

        if (sku.isBlank() || name.isBlank() || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            result.addNote(displayRow, "Bỏ qua: thiếu Mã hàng/Tên hàng/Giá bán hợp lệ");
            return;
        }

        if (stockQuantity != null && stockQuantity < 0) {
            // KiotViet exports a negative "Tồn kho" for an oversold item.
            // Product.stockQuantity is @Min(0), so storing it as-is fails the
            // row outright - keep the product, record what its sheet said.
            result.addNote(displayRow, "Tồn kho âm (%d) - đã nhập với tồn kho 0".formatted(stockQuantity));
            stockQuantity = 0;
        }

        Optional<Product> existingBySku = productRepository.findBySkuAndStoreId(sku, state.storeId);
        Optional<Product> existingByBarcode = barcode.isBlank()
                ? Optional.empty()
                : productRepository.findByBarcodeAndStoreId(barcode, state.storeId);

        if (existingBySku.isPresent()) {
            Product existing = existingBySku.get();
            if (!existing.getName().trim().equals(name)) {
                if (!options.replaceDuplicateName()) {
                    result.setStoppedAtRow(displayRow);
                    result.setStopReason("Dòng %d: Mã hàng \"%s\" đã tồn tại với tên khác (\"%s\")"
                            .formatted(displayRow, sku, existing.getName()));
                    throw new StopImportException();
                }
                existing.setName(name);
            }
            applyUpdatableFields(existing, price, costPrice, stockQuantity, minStockThreshold,
                    maxStockThreshold, brand, productType, unitName, description, options);
            queueImages(productRepository.save(existing), cells, state);
            maybeFlush(state);
            result.setUpdatedCount(result.getUpdatedCount() + 1);
            if (!baseUnitSku.isBlank()) {
                state.pendingUnitLinks.add(new PendingUnitLink(sku, baseUnitSku, displayRow));
            }
            return;
        }

        // A KiotViet alternate-unit row ("thùng 100 túi") gets its own Mã hàng
        // but repeats its base unit's Mã vạch, and names that base in "Mã ĐVT
        // Cơ bản" - exactly what rows 435/436 of a real export look like. That
        // is a sibling, not the mistyped-barcode clash this option guards
        // against, so importing must not halt on it: a real file hits its first
        // such pair within a few hundred rows and stops there.
        if (existingByBarcode.isPresent() && baseUnitSku.equals(existingByBarcode.get().getSku())) {
            result.addNote(displayRow, "Mã vạch \"%s\" đã thuộc mã hàng %s (đơn vị cơ bản) - đã nhập không kèm mã vạch"
                    .formatted(barcode, existingByBarcode.get().getSku()));
            // Dropped rather than shared: findByBarcode returns a single
            // Product - one product per scanned code is what the POS counts on
            // - so a second row carrying this barcode would break every later
            // lookup of it. Scanning it rings up the base unit, which is right.
            barcode = "";
            existingByBarcode = Optional.empty();
        }

        if (existingByBarcode.isPresent()) {
            Product existing = existingByBarcode.get();
            if (!options.replaceDuplicateSku()) {
                result.setStoppedAtRow(displayRow);
                result.setStopReason("Dòng %d: Mã vạch \"%s\" đã tồn tại với mã hàng khác (\"%s\")"
                        .formatted(displayRow, barcode, existing.getSku()));
                throw new StopImportException();
            }
            existing.setSku(sku);
            applyUpdatableFields(existing, price, costPrice, stockQuantity, minStockThreshold,
                    maxStockThreshold, brand, productType, unitName, description, options);
            queueImages(productRepository.save(existing), cells, state);
            maybeFlush(state);
            result.setUpdatedCount(result.getUpdatedCount() + 1);
            if (!baseUnitSku.isBlank()) {
                state.pendingUnitLinks.add(new PendingUnitLink(sku, baseUnitSku, displayRow));
            }
            return;
        }

        try {
            subscriptionGuard.requireCanAddProduct(state.storeId, state.currentProductCount);
        } catch (RuntimeException e) {
            result.setStoppedAtRow(displayRow);
            result.setStopReason("Dòng %d: %s".formatted(displayRow, e.getMessage()));
            throw new StopImportException();
        }

        Product product = new Product();
        product.setStore(state.storeRef);
        product.setName(name);
        product.setSlug(uniqueSlug(SlugUtil.slugify(name), state));
        product.setSku(sku);
        product.setBarcode(barcode.isBlank() ? null : barcode);
        product.setPrice(price);
        product.setCostPrice(costPrice);
        product.setStockQuantity(stockQuantity != null ? stockQuantity : 0);
        product.setMinStockThreshold(minStockThreshold);
        product.setMaxStockThreshold(maxStockThreshold);
        product.setBrand(brand.isBlank() ? null : brand);
        product.setProductType(productType.isBlank() ? DEFAULT_PRODUCT_TYPE : productType);
        product.setDescription(description.isBlank() ? null : description);
        product.setActive(true);
        if (!unitName.isBlank()) {
            product.getAttributes().put(UNIT_ATTRIBUTE_NAME, unitName);
        }

        if (!categoryPath.isBlank()) {
            Category category = resolveCategoryPath(categoryPath, state);
            if (category != null) {
                // Deliberately NOT product.addCategory(category): that helper
                // also does category.getProducts().add(product), and
                // Category.products is a lazy @ManyToMany(mappedBy). Once
                // maybeFlush() has cleared the persistence context, a category
                // held in categoryPathCache is detached, so touching that
                // collection threw "failed to lazily initialize a collection of
                // role: Category.products - no Session" and skipped the row
                // (before per-row isolation it took the whole import down).
                // Even attached it would be wrong here: initializing it loads
                // every product already in that category, once per imported row.
                // Product.categories is the owning side, so adding here is all
                // the join row needs.
                product.getCategories().add(category);
            }
        }

        queueImages(productRepository.save(product), cells, state);
        maybeFlush(state);
        state.currentProductCount++;
        result.setCreatedCount(result.getCreatedCount() + 1);
        if (!baseUnitSku.isBlank()) {
            state.pendingUnitLinks.add(new PendingUnitLink(sku, baseUnitSku, displayRow));
        }
    }

    /**
     * Every FLUSH_INTERVAL saved rows, clears the Hibernate persistence
     * context so it doesn't accumulate every touched Product (each with
     * several eager @ElementCollection fields) for the rest of the request -
     * see FLUSH_INTERVAL's javadoc. Only clear() is needed, not flush():
     * productRepository.save(...) is itself a Spring Data-managed
     * @Transactional method, so by the time control returns here that row's
     * change has ALREADY committed in its own transaction - importFromExcel
     * deliberately isn't @Transactional itself (see its class javadoc: rows
     * commit independently so a later row's failure doesn't undo earlier
     * ones), so there is no open transaction at this point for flush() to
     * synchronize - calling it here would throw TransactionRequiredException.
     * clear() itself doesn't touch the database, so it needs no transaction.
     *
     * Safe to clear mid-import here: nothing in state holds an entity whose
     * *fields* (rather than just its id) get read after this point -
     * categoryPathCache and storeRef are only ever reused as an
     * association's FK target (Product.store and Product.categories are
     * both plain @ManyToOne/@ManyToMany with no persist/merge cascade),
     * which Hibernate resolves from the id alone even once the object
     * backing it is detached.
     */
    private void maybeFlush(ImportState state) {
        if (++state.unflushedRows >= FLUSH_INTERVAL) {
            entityManager.clear();
            state.unflushedRows = 0;
        }
    }

    private void applyUpdatableFields(
            Product existing, BigDecimal price, BigDecimal costPrice, Integer stockQuantity,
            Integer minStockThreshold, Integer maxStockThreshold, String brand, String productType,
            String unitName, String description, ProductImportOptions options) {
        existing.setPrice(price);
        if (options.updateStock() && stockQuantity != null) {
            existing.setStockQuantity(stockQuantity);
        }
        if (options.updateCostPrice() && costPrice != null) {
            existing.setCostPrice(costPrice);
        }
        if (minStockThreshold != null) {
            existing.setMinStockThreshold(minStockThreshold);
        }
        if (maxStockThreshold != null) {
            existing.setMaxStockThreshold(maxStockThreshold);
        }
        if (!brand.isBlank()) {
            existing.setBrand(brand);
        }
        if (!productType.isBlank()) {
            existing.setProductType(productType);
        }
        if (!unitName.isBlank()) {
            existing.getAttributes().put(UNIT_ATTRIBUTE_NAME, unitName);
        }
        if (options.updateDescription() && !description.isBlank()) {
            existing.setDescription(description);
        }
    }

    /**
     * Resolves a "Dịch vụ&gt;&gt;Gói quà"-style path into its leaf Category,
     * creating any missing level under its parent (store-scoped, capped at
     * MAX_CATEGORY_DEPTH levels - matching KiotViet's "Nhóm hàng (3 Cấp)"
     * label). Cached per import call so a path repeated across many rows
     * only hits the DB once.
     */
    private Category resolveCategoryPath(String path, ImportState state) {
        Category parent = null;
        StringBuilder cacheKeyBuilder = new StringBuilder();
        int depth = 0;
        for (String rawSegment : path.split(CATEGORY_PATH_SEPARATOR)) {
            if (depth >= MAX_CATEGORY_DEPTH) {
                break;
            }
            String segmentName = rawSegment.trim();
            if (segmentName.isBlank()) {
                continue;
            }
            cacheKeyBuilder.append('/').append(segmentName.toLowerCase());
            String cacheKey = cacheKeyBuilder.toString();
            Category segment = state.categoryPathCache.get(cacheKey);
            if (segment == null) {
                Category parentRef = parent;
                segment = categoryRepository.findByNameIgnoreCaseAndParentAndStoreId(segmentName, parentRef, state.storeId)
                        .orElseGet(() -> createCategory(segmentName, parentRef, state));
                state.categoryPathCache.put(cacheKey, segment);
            }
            parent = segment;
            depth++;
        }
        return parent;
    }

    private Category createCategory(String name, Category parent, ImportState state) {
        Category category = new Category();
        category.setName(name);
        category.setSlug(uniqueCategorySlug(SlugUtil.slugify(name), state.storeId));
        category.setStore(state.storeRef);
        category.setActive(true);
        category.setParent(parent);
        return categoryRepository.save(category);
    }

    private String uniqueCategorySlug(String baseSlug, Long storeId) {
        String candidate = baseSlug;
        int suffix = 2;
        while (categoryRepository.existsBySlugAndStoreId(candidate, storeId)) {
            candidate = baseSlug + "-" + suffix++;
        }
        return candidate;
    }

    /**
     * Second pass: links each row that named a "Mã ĐVT Cơ bản" to that base
     * SKU's product by sharing one variantGroupId, the same grouping
     * AdminProductController#createProductVariants uses. Runs after every
     * row has already been saved so a base unit can be referenced whether it
     * appears earlier or later in the sheet.
     */
    private void linkUnitVariants(List<PendingUnitLink> pendingUnitLinks, ProductImportResult result, Long storeId) {
        int unflushed = 0; // same flush/clear rationale as maybeFlush(ImportState) above
        for (PendingUnitLink link : pendingUnitLinks) {
            if (link.sku().equals(link.baseUnitSku())) {
                result.addNote(link.displayRow(), "Mã ĐVT Cơ bản không thể trùng với chính hàng hóa này");
                continue;
            }
            Optional<Product> derived = productRepository.findBySkuAndStoreId(link.sku(), storeId);
            Optional<Product> base = productRepository.findBySkuAndStoreId(link.baseUnitSku(), storeId);
            if (derived.isEmpty() || base.isEmpty()) {
                result.addNote(link.displayRow(), "Không tìm thấy Mã ĐVT Cơ bản \"%s\"".formatted(link.baseUnitSku()));
                continue;
            }
            Product derivedProduct = derived.get();
            Product baseProduct = base.get();

            String groupId = baseProduct.getVariantGroupId() != null
                    ? baseProduct.getVariantGroupId()
                    : derivedProduct.getVariantGroupId() != null
                            ? derivedProduct.getVariantGroupId()
                            : UUID.randomUUID().toString();

            if (!groupId.equals(baseProduct.getVariantGroupId())) {
                baseProduct.setVariantGroupId(groupId);
                productRepository.save(baseProduct);
                unflushed++;
            }
            if (!groupId.equals(derivedProduct.getVariantGroupId())) {
                derivedProduct.setVariantGroupId(groupId);
                productRepository.save(derivedProduct);
                unflushed++;
            }
            if (unflushed >= FLUSH_INTERVAL) {
                // No flush() here either - see maybeFlush(ImportState)'s javadoc:
                // each save() above already committed in its own transaction.
                entityManager.clear();
                unflushed = 0;
            }
        }
    }

    private record PendingUnitLink(String sku, String baseUnitSku, int displayRow) {
    }

    private boolean isBlankRow(String[] cells, ProductImportColumns columns) {
        return cellStr(cells, columns.sku()).isBlank() && cellStr(cells, columns.name()).isBlank();
    }

    /**
     * Hands the row's picture links to {@link ProductImageImportService} -
     * queued here, uploaded after the whole sheet is read, so a slow
     * Cloudinary fetch never delays the row that follows.
     *
     * Only ids and URLs are kept: the Product itself may be detached by the
     * next maybeFlush(), and the background job re-reads whatever it needs.
     */
    private void queueImages(Product product, String[] cells, ImportState state) {
        if (product.getId() == null) {
            return;
        }
        List<String> urls = parseImageUrls(cellStr(cells, state.columns.imageUrls()));
        if (!urls.isEmpty()) {
            state.pendingImages.add(new ProductImageImportService.PendingProductImages(
                    product.getId(), product.getName(), urls));
        }
    }

    /**
     * Reads a "url1,url2,..." cell by cutting it at every http(s) scheme it
     * contains, rather than splitting on the comma the header advertises: a
     * Cloudinary URL legitimately carries commas inside its transformation
     * segment (".../w_300,h_300,c_fill/..."), and a comma split would tear
     * one such link into unusable pieces. Scanning for the scheme also means
     * a cell holding something that is NOT a link (a Windows path, a bare
     * file name) yields nothing at all, so it never reaches Cloudinary -
     * where a non-URL string would be read as a local server file path (see
     * CloudinaryService#isFetchableImageUrl).
     */
    private List<String> parseImageUrls(String cell) {
        if (cell.isBlank()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        int start = indexOfScheme(cell, 0);
        while (start >= 0 && urls.size() < MAX_IMAGES_PER_PRODUCT) {
            int next = indexOfScheme(cell, start + 1);
            String url = trimTrailingSeparators(next < 0 ? cell.substring(start) : cell.substring(start, next));
            if (!url.isEmpty() && !urls.contains(url)) {
                urls.add(url);
            }
            start = next;
        }
        return urls;
    }

    /** Index of the next "http://" or "https://" at or after {@code from}, or -1. */
    private int indexOfScheme(String cell, int from) {
        int http = cell.indexOf("http://", from);
        int https = cell.indexOf("https://", from);
        if (http < 0) {
            return https;
        }
        return https < 0 ? http : Math.min(http, https);
    }

    private String trimTrailingSeparators(String url) {
        String trimmed = url.trim();
        while (!trimmed.isEmpty()) {
            char last = trimmed.charAt(trimmed.length() - 1);
            if (last != ',' && last != ';' && last != '|' && !Character.isWhitespace(last)) {
                break;
            }
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /** Appends -2, -3, ... on collision against both this store's rows and other rows in this same batch (same approach as AdminProductController#uniqueSlug). */
    private String uniqueSlug(String baseSlug, ImportState state) {
        String candidate = baseSlug;
        int suffix = 2;
        while (state.usedSlugsInBatch.contains(candidate)
                || productRepository.existsBySlugAndStoreId(candidate, state.storeId)) {
            candidate = baseSlug + "-" + suffix++;
        }
        state.usedSlugsInBatch.add(candidate);
        return candidate;
    }

    /** Reads one cell; a column the sheet doesn't have (ProductImportColumns.ABSENT, i.e. a negative index) reads as blank. */
    private String cellStr(String[] cells, int idx) {
        String v = idx >= 0 && idx < cells.length ? cells[idx] : null;
        return v == null ? "" : v.trim();
    }

    private BigDecimal cellDecimal(String[] cells, int idx) {
        String s = cellStr(cells, idx).replace(",", "").trim();
        if (s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer cellInt(String[] cells, int idx) {
        BigDecimal d = cellDecimal(cells, idx);
        return d == null ? null : d.intValue();
    }
}

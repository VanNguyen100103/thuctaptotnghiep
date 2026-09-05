package com.ut.edu.backend.product;

import com.ut.edu.backend.category.Category;
import com.ut.edu.backend.category.CategoryRepository;
import com.ut.edu.backend.common.SlugUtil;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;

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
import javax.xml.parsers.ParserConfigurationException;

/**
 * Bulk product import from an .xlsx file, matching KiotViet's own "Nhập hàng
 * hóa từ file dữ liệu" template column-for-column (headers, order, and
 * styling - a filled/bordered header row with filter dropdowns, like
 * KiotViet's own export). Column layout (fixed, matches generateTemplate()):
 * 0 Loại hàng | 1 Nhóm hàng(3 Cấp) | 2 Mã hàng | 3 Mã vạch | 4 Tên hàng |
 * 5 Thương hiệu | 6 Giá bán | 7 Giá vốn | 8 Tồn kho | 9 Tồn nhỏ nhất |
 * 10 Tồn lớn nhất | 11 ĐVT | 12 Mã ĐVT Cơ bản | 13 Quy đổi | 14 Mô tả.
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

    private static final int MAX_ROWS = 5000;
    private static final int MAX_CATEGORY_DEPTH = 3;
    private static final String CATEGORY_PATH_SEPARATOR = ">>";
    private static final String UNIT_ATTRIBUTE_NAME = "Đơn vị tính";
    private static final String DEFAULT_PRODUCT_TYPE = "Hàng hóa";

    private static final String[] HEADERS = {
            "Loại hàng", "Nhóm hàng(3 Cấp)", "Mã hàng", "Mã vạch", "Tên hàng", "Thương hiệu",
            "Giá bán", "Giá vốn", "Tồn kho", "Tồn nhỏ nhất", "Tồn lớn nhất", "ĐVT",
            "Mã ĐVT Cơ bản", "Quy đổi", "Mô tả",
    };

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
            {"Hàng hóa", "Kẹo bánh", "HH000026", "364332862", "Kẹo Doublemint", "Doublemint", "10000", "8000", "5", "0", "50", "Hộp", "", "1", ""},
            {"Hàng hóa", "Kẹo bánh", "HH000025", "695588910", "Kẹo cao su tổng hợp", "", "10000", "8000", "5", "0", "50", "Hộp", "", "1", ""},
            {"Hàng hóa", "Mỹ phẩm", "HH000023", "824804043", "Sữa tắm Palmolive xanh lá", "Colgate", "10000", "8000", "10", "0", "50", "Lọ", "", "1", ""},
            {"Hàng hóa", "Mỹ phẩm", "HH000016", "720467868", "Kem dưỡng da Johnson xanh", "Johnson & Johnson", "3000", "1000", "10", "0", "50", "Lọ", "", "1", ""},
            {"Hàng hóa", "Mỹ phẩm", "HH000015", "421176476", "Kem dưỡng da Johnson xanh", "Johnson & Johnson", "30000", "10000", "5", "0", "50", "Thùng", "HH000016", "10", ""},
            {"Hàng hóa", "Thực phẩm", "HH000011", "284018188", "Phở bò phở cổ", "", "39000", "25000", "15", "0", "50", "Gói", "", "1", ""},
            {"Hàng hóa", "Thực phẩm", "HH000009", "441382011", "Thịt bò khô 30g", "", "60000", "48000", "5", "0", "50", "Gói", "", "1", ""},
            {"Dịch vụ", "Dịch vụ>>Gói quà", "HH000008", "297019677", "Gói quà", "", "180000", "180000", "0", "0", "0", "", "", "", ""},
            {"Dịch vụ", "Dịch vụ>>Rửa xe", "HH000099", "360601057", "Rửa xe", "", "350000", "300000", "0", "0", "100", "", "", "", ""},
            {"Combo", "Mỹ phẩm", "HH000010", "622840957", "Set mỹ phẩm tổng hợp", "", "200000", "142000", "5", "0", "50", "Set", "", "1", ""},
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

    @PersistenceContext
    private EntityManager entityManager;

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
    public ProductImportResult importFromExcel(MultipartFile file, ProductImportOptions options) {
        Long storeId = tenantGuard.requireStore();
        Store storeRef = tenantGuard.currentStoreRef();
        ProductImportResult result = new ProductImportResult();
        ImportState state = new ImportState(storeId, storeRef, productRepository.countByStoreId(storeId), options, result);

        File tempFile = null;
        try {
            // Written to a real file (rather than parsed straight off the
            // multipart InputStream) so OPCPackage can open it with true
            // random-file-access reads - an InputStream-backed OPCPackage
            // has to buffer the whole zip into memory first, since ZIP
            // central-directory lookups need seekable access.
            tempFile = File.createTempFile("product-import-", ".xlsx");
            file.transferTo(tempFile);

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
            throw new IllegalArgumentException("Không đọc được file - vui lòng dùng đúng file mẫu .xlsx", e);
        } finally {
            if (tempFile != null && !tempFile.delete()) {
                log.warn("Failed to delete temp import file {}", tempFile);
            }
        }

        linkUnitVariants(state.pendingUnitLinks, result);

        log.info("Product import for store {}: {} created, {} updated, {} total rows{}",
                storeId, result.getCreatedCount(), result.getUpdatedCount(), result.getTotalRows(),
                result.getStoppedAtRow() != null ? ", stopped at row " + result.getStoppedAtRow() : "");
        return result;
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
                    currentRow = new String[HEADERS.length];
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
                    if (currentRowNum >= 1) {
                        processDataRow(currentRow, currentRowNum, state);
                    }
                    if (currentRowNum >= MAX_ROWS) {
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

    /** One data row's worth of the old importFromExcel loop body, ported to read from a raw String[] instead of a POI Row. */
    private void processDataRow(String[] cells, int rowIndex, ImportState state) {
        if (isBlankRow(cells)) {
            return;
        }
        ProductImportResult result = state.result;
        ProductImportOptions options = state.options;
        result.setTotalRows(result.getTotalRows() + 1);
        int displayRow = rowIndex + 1; // 1-based spreadsheet row number for messages

        String productType = cellStr(cells, 0);
        String categoryPath = cellStr(cells, 1);
        String sku = cellStr(cells, 2);
        String barcode = cellStr(cells, 3);
        String name = cellStr(cells, 4);
        String brand = cellStr(cells, 5);
        BigDecimal price = cellDecimal(cells, 6);
        BigDecimal costPrice = cellDecimal(cells, 7);
        Integer stockQuantity = cellInt(cells, 8);
        Integer minStockThreshold = cellInt(cells, 9);
        Integer maxStockThreshold = cellInt(cells, 10);
        String unitName = cellStr(cells, 11);
        // A real KiotViet export can write a literal "0" into this numeric-
        // looking column for an ordinary single-unit row instead of leaving
        // it blank (seen in production testing). No real product is ever
        // coded "0" (this template's own sample SKUs all look like
        // "HH000016"), so treat "0" the same as blank rather than reporting
        // a "Mã ĐVT Cơ bản not found" note on nearly every row.
        String baseUnitSku = "0".equals(cellStr(cells, 12)) ? "" : cellStr(cells, 12);
        // Column 13 "Quy đổi" is intentionally unread - see class javadoc.
        String description = cellStr(cells, 14);

        if (sku.isBlank() || name.isBlank() || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            result.addNote(displayRow, "Bỏ qua: thiếu Mã hàng/Tên hàng/Giá bán hợp lệ");
            return;
        }

        Optional<Product> existingBySku = productRepository.findBySku(sku);
        Optional<Product> existingByBarcode = barcode.isBlank()
                ? Optional.empty()
                : productRepository.findByBarcode(barcode);

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
            productRepository.save(existing);
            maybeFlush(state);
            result.setUpdatedCount(result.getUpdatedCount() + 1);
            if (!baseUnitSku.isBlank()) {
                state.pendingUnitLinks.add(new PendingUnitLink(sku, baseUnitSku, displayRow));
            }
            return;
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
            productRepository.save(existing);
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
        product.setSlug(uniqueSlug(SlugUtil.slugify(name), state.usedSlugsInBatch));
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
            Category category = resolveCategoryPath(categoryPath, state.storeRef, state.categoryPathCache);
            if (category != null) {
                product.addCategory(category);
            }
        }

        productRepository.save(product);
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
    private Category resolveCategoryPath(String path, Store storeRef, Map<String, Category> cache) {
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
            Category segment = cache.get(cacheKey);
            if (segment == null) {
                Category parentRef = parent;
                segment = categoryRepository.findByNameIgnoreCaseAndParent(segmentName, parentRef)
                        .orElseGet(() -> createCategory(segmentName, parentRef, storeRef));
                cache.put(cacheKey, segment);
            }
            parent = segment;
            depth++;
        }
        return parent;
    }

    private Category createCategory(String name, Category parent, Store storeRef) {
        Category category = new Category();
        category.setName(name);
        category.setSlug(uniqueCategorySlug(SlugUtil.slugify(name)));
        category.setStore(storeRef);
        category.setActive(true);
        category.setParent(parent);
        return categoryRepository.save(category);
    }

    private String uniqueCategorySlug(String baseSlug) {
        String candidate = baseSlug;
        int suffix = 2;
        while (Boolean.TRUE.equals(categoryRepository.existsBySlug(candidate))) {
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
    private void linkUnitVariants(List<PendingUnitLink> pendingUnitLinks, ProductImportResult result) {
        int unflushed = 0; // same flush/clear rationale as maybeFlush(ImportState) above
        for (PendingUnitLink link : pendingUnitLinks) {
            if (link.sku().equals(link.baseUnitSku())) {
                result.addNote(link.displayRow(), "Mã ĐVT Cơ bản không thể trùng với chính hàng hóa này");
                continue;
            }
            Optional<Product> derived = productRepository.findBySku(link.sku());
            Optional<Product> base = productRepository.findBySku(link.baseUnitSku());
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

    private boolean isBlankRow(String[] cells) {
        return cellStr(cells, 2).isBlank() && cellStr(cells, 4).isBlank();
    }

    /** Appends -2, -3, ... on collision against both the DB and other rows in this same batch (same approach as AdminProductController#uniqueSlug). */
    private String uniqueSlug(String baseSlug, Set<String> usedInBatch) {
        String candidate = baseSlug;
        int suffix = 2;
        while (usedInBatch.contains(candidate) || Boolean.TRUE.equals(productRepository.existsBySlug(candidate))) {
            candidate = baseSlug + "-" + suffix++;
        }
        usedInBatch.add(candidate);
        return candidate;
    }

    private String cellStr(String[] cells, int idx) {
        String v = idx < cells.length ? cells[idx] : null;
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

package com.ut.edu.backend.product;

import com.ut.edu.backend.category.Category;
import com.ut.edu.backend.category.CategoryRepository;
import com.ut.edu.backend.common.SlugUtil;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final TenantGuard tenantGuard;
    private final SubscriptionGuard subscriptionGuard;

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

    public ProductImportResult importFromExcel(MultipartFile file, ProductImportOptions options) {
        Long storeId = tenantGuard.requireStore();
        Store storeRef = tenantGuard.currentStoreRef();
        long currentProductCount = productRepository.countByStoreId(storeId);
        Set<String> usedSlugsInBatch = new HashSet<>();
        Map<String, Category> categoryPathCache = new HashMap<>();
        List<PendingUnitLink> pendingUnitLinks = new ArrayList<>();

        ProductImportResult result = new ProductImportResult();

        Sheet sheet;
        try (var inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            sheet = workbook.getSheetAt(0);
            int lastRow = Math.min(sheet.getLastRowNum(), MAX_ROWS);

            for (int rowIndex = 1; rowIndex <= lastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                result.setTotalRows(result.getTotalRows() + 1);
                int displayRow = rowIndex + 1; // 1-based spreadsheet row number for messages

                String productType = cellString(row, 0);
                String categoryPath = cellString(row, 1);
                String sku = cellString(row, 2);
                String barcode = cellString(row, 3);
                String name = cellString(row, 4);
                String brand = cellString(row, 5);
                BigDecimal price = cellDecimal(row, 6);
                BigDecimal costPrice = cellDecimal(row, 7);
                Integer stockQuantity = cellInt(row, 8);
                Integer minStockThreshold = cellInt(row, 9);
                Integer maxStockThreshold = cellInt(row, 10);
                String unitName = cellString(row, 11);
                String baseUnitSku = cellString(row, 12);
                // Column 13 "Quy đổi" is intentionally unread - see class javadoc.
                String description = cellString(row, 14);

                if (sku.isBlank() || name.isBlank() || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                    result.addNote(displayRow, "Bỏ qua: thiếu Mã hàng/Tên hàng/Giá bán hợp lệ");
                    continue;
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
                            break;
                        }
                        existing.setName(name);
                    }
                    applyUpdatableFields(existing, price, costPrice, stockQuantity, minStockThreshold,
                            maxStockThreshold, brand, productType, unitName, description, options);
                    productRepository.save(existing);
                    result.setUpdatedCount(result.getUpdatedCount() + 1);
                    if (!baseUnitSku.isBlank()) {
                        pendingUnitLinks.add(new PendingUnitLink(sku, baseUnitSku, displayRow));
                    }
                    continue;
                }

                if (existingByBarcode.isPresent()) {
                    Product existing = existingByBarcode.get();
                    if (!options.replaceDuplicateSku()) {
                        result.setStoppedAtRow(displayRow);
                        result.setStopReason("Dòng %d: Mã vạch \"%s\" đã tồn tại với mã hàng khác (\"%s\")"
                                .formatted(displayRow, barcode, existing.getSku()));
                        break;
                    }
                    existing.setSku(sku);
                    applyUpdatableFields(existing, price, costPrice, stockQuantity, minStockThreshold,
                            maxStockThreshold, brand, productType, unitName, description, options);
                    productRepository.save(existing);
                    result.setUpdatedCount(result.getUpdatedCount() + 1);
                    if (!baseUnitSku.isBlank()) {
                        pendingUnitLinks.add(new PendingUnitLink(sku, baseUnitSku, displayRow));
                    }
                    continue;
                }

                try {
                    subscriptionGuard.requireCanAddProduct(storeId, currentProductCount);
                } catch (RuntimeException e) {
                    result.setStoppedAtRow(displayRow);
                    result.setStopReason("Dòng %d: %s".formatted(displayRow, e.getMessage()));
                    break;
                }

                Product product = new Product();
                product.setStore(storeRef);
                product.setName(name);
                product.setSlug(uniqueSlug(SlugUtil.slugify(name), usedSlugsInBatch));
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
                    Category category = resolveCategoryPath(categoryPath, storeRef, categoryPathCache);
                    if (category != null) {
                        product.addCategory(category);
                    }
                }

                productRepository.save(product);
                currentProductCount++;
                result.setCreatedCount(result.getCreatedCount() + 1);
                if (!baseUnitSku.isBlank()) {
                    pendingUnitLinks.add(new PendingUnitLink(sku, baseUnitSku, displayRow));
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Không đọc được file - vui lòng dùng đúng file mẫu .xlsx", e);
        }

        linkUnitVariants(pendingUnitLinks, result);

        log.info("Product import for store {}: {} created, {} updated, {} total rows{}",
                storeId, result.getCreatedCount(), result.getUpdatedCount(), result.getTotalRows(),
                result.getStoppedAtRow() != null ? ", stopped at row " + result.getStoppedAtRow() : "");
        return result;
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
            }
            if (!groupId.equals(derivedProduct.getVariantGroupId())) {
                derivedProduct.setVariantGroupId(groupId);
                productRepository.save(derivedProduct);
            }
        }
    }

    private record PendingUnitLink(String sku, String baseUnitSku, int displayRow) {
    }

    private boolean isBlankRow(Row row) {
        return cellString(row, 2).isBlank() && cellString(row, 4).isBlank();
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

    private String cellString(Row row, int idx) {
        Cell cell = row.getCell(idx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                yield v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (IllegalStateException e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private BigDecimal cellDecimal(Row row, int idx) {
        String s = cellString(row, idx).replace(",", "").trim();
        if (s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer cellInt(Row row, int idx) {
        BigDecimal d = cellDecimal(row, idx);
        return d == null ? null : d.intValue();
    }
}

package com.ut.edu.backend.product;

import com.ut.edu.backend.category.Category;
import com.ut.edu.backend.category.CategoryRepository;
import com.ut.edu.backend.common.SlugUtil;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Bulk product import from an .xlsx file, matching KiotViet's "Nhập hàng hóa
 * từ file dữ liệu" dialog. Column layout (fixed, matches generateTemplate()):
 * 0 Mã hàng* | 1 Mã vạch | 2 Tên hàng hóa* | 3 Danh mục | 4 Giá bán* |
 * 5 Giá vốn | 6 Tồn kho | 7 Mô tả.
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
    private static final String[] HEADERS = {
            "Mã hàng*", "Mã vạch", "Tên hàng hóa*", "Danh mục", "Giá bán*", "Giá vốn", "Tồn kho", "Mô tả",
    };

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final TenantGuard tenantGuard;
    private final SubscriptionGuard subscriptionGuard;

    public byte[] generateTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Hàng hóa");

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            XSSFCellStyle headerStyle = (XSSFCellStyle) workbook.createCellStyle();
            headerStyle.setFont(boldFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 20 * 256);
            }

            Row example = sheet.createRow(1);
            String[] exampleValues = {"SP0001", "", "Sản phẩm mẫu", "", "100000", "70000", "10", ""};
            for (int i = 0; i < exampleValues.length; i++) {
                example.createCell(i).setCellValue(exampleValues[i]);
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

                String sku = cellString(row, 0);
                String barcode = cellString(row, 1);
                String name = cellString(row, 2);
                String categoryName = cellString(row, 3);
                BigDecimal price = cellDecimal(row, 4);
                BigDecimal costPrice = cellDecimal(row, 5);
                Integer stockQuantity = cellInt(row, 6);
                String description = cellString(row, 7);

                if (sku.isBlank() || name.isBlank() || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                    result.addNote(displayRow, "Bỏ qua: thiếu Mã hàng/Tên hàng hóa/Giá bán hợp lệ");
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
                    applyUpdatableFields(existing, price, costPrice, stockQuantity, description, options);
                    productRepository.save(existing);
                    result.setUpdatedCount(result.getUpdatedCount() + 1);
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
                    applyUpdatableFields(existing, price, costPrice, stockQuantity, description, options);
                    productRepository.save(existing);
                    result.setUpdatedCount(result.getUpdatedCount() + 1);
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
                product.setDescription(description.isBlank() ? null : description);
                product.setActive(true);

                if (!categoryName.isBlank()) {
                    Optional<Category> category = categoryRepository.findByNameIgnoreCase(categoryName);
                    if (category.isPresent()) {
                        product.addCategory(category.get());
                    } else {
                        result.addNote(displayRow, "Không tìm thấy danh mục \"%s\" - đã tạo không có danh mục".formatted(categoryName));
                    }
                }

                productRepository.save(product);
                currentProductCount++;
                result.setCreatedCount(result.getCreatedCount() + 1);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Không đọc được file - vui lòng dùng đúng file mẫu .xlsx", e);
        }

        log.info("Product import for store {}: {} created, {} updated, {} total rows{}",
                storeId, result.getCreatedCount(), result.getUpdatedCount(), result.getTotalRows(),
                result.getStoppedAtRow() != null ? ", stopped at row " + result.getStoppedAtRow() : "");
        return result;
    }

    private void applyUpdatableFields(
            Product existing, BigDecimal price, BigDecimal costPrice, Integer stockQuantity,
            String description, ProductImportOptions options) {
        existing.setPrice(price);
        if (options.updateStock() && stockQuantity != null) {
            existing.setStockQuantity(stockQuantity);
        }
        if (options.updateCostPrice() && costPrice != null) {
            existing.setCostPrice(costPrice);
        }
        if (options.updateDescription() && !description.isBlank()) {
            existing.setDescription(description);
        }
    }

    private boolean isBlankRow(Row row) {
        return cellString(row, 0).isBlank() && cellString(row, 2).isBlank();
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

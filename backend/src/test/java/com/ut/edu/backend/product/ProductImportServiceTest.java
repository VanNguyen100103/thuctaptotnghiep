package com.ut.edu.backend.product;

import com.ut.edu.backend.category.Category;
import com.ut.edu.backend.category.CategoryRepository;
import com.ut.edu.backend.exception.SubscriptionRequiredException;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductImportServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TenantGuard tenantGuard;
    @Mock private SubscriptionGuard subscriptionGuard;

    @InjectMocks
    private ProductImportService importService;

    private Store store;

    @BeforeEach
    void setUp() {
        store = Store.builder().id(10L).name("Test Store").build();
        when(tenantGuard.requireStore()).thenReturn(10L);
        when(tenantGuard.currentStoreRef()).thenReturn(store);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private MockMultipartFile fileOf(String[]... rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Mã hàng*");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null) {
                        row.createCell(c).setCellValue(rows[r][c]);
                    }
                }
            }
            workbook.write(out);
            return new MockMultipartFile("file", "import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final ProductImportOptions DEFAULTS = new ProductImportOptions(false, false, false, false, false);

    @Test
    void generateTemplate_producesAReadableWorkbookWithExpectedHeaders() throws IOException {
        byte[] bytes = importService.generateTemplate();

        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Mã hàng*");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Tên hàng hóa*");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Giá bán*");
            assertThat(sheet.getRow(1)).isNotNull();
        }
    }

    @Test
    void import_newProduct_createsIt() {
        when(productRepository.findBySku("SP001")).thenReturn(Optional.empty());
        when(productRepository.existsBySlug(any())).thenReturn(false);

        MockMultipartFile file = fileOf(new String[]{"SP001", "", "Áo thun", "", "100000", "70000", "10", "Mô tả"});
        ProductImportResult result = importService.importFromExcel(file, DEFAULTS);

        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getUpdatedCount()).isZero();
        assertThat(result.getStoppedAtRow()).isNull();
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void import_matchingSkuSameName_updatesPriceOnlyByDefault() {
        Product existing = Product.builder().id(1L).sku("SP001").name("Áo thun")
                .price(BigDecimal.valueOf(50000)).costPrice(BigDecimal.valueOf(30000))
                .stockQuantity(5).description("Cũ").build();
        when(productRepository.findBySku("SP001")).thenReturn(Optional.of(existing));

        MockMultipartFile file = fileOf(new String[]{"SP001", "", "Áo thun", "", "100000", "70000", "10", "Mới"});
        ProductImportResult result = importService.importFromExcel(file, DEFAULTS);

        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(existing.getPrice()).isEqualByComparingTo("100000");
        assertThat(existing.getStockQuantity()).isEqualTo(5); // untouched - updateStock=false
        assertThat(existing.getCostPrice()).isEqualByComparingTo("30000"); // untouched
        assertThat(existing.getDescription()).isEqualTo("Cũ"); // untouched
    }

    @Test
    void import_matchingSku_updatesStockCostDescription_whenFlagsSet() {
        Product existing = Product.builder().id(1L).sku("SP001").name("Áo thun")
                .price(BigDecimal.valueOf(50000)).costPrice(BigDecimal.valueOf(30000))
                .stockQuantity(5).description("Cũ").build();
        when(productRepository.findBySku("SP001")).thenReturn(Optional.of(existing));

        ProductImportOptions options = new ProductImportOptions(false, false, true, true, true);
        MockMultipartFile file = fileOf(new String[]{"SP001", "", "Áo thun", "", "100000", "70000", "10", "Mới"});
        importService.importFromExcel(file, options);

        assertThat(existing.getStockQuantity()).isEqualTo(10);
        assertThat(existing.getCostPrice()).isEqualByComparingTo("70000");
        assertThat(existing.getDescription()).isEqualTo("Mới");
    }

    @Test
    void import_duplicateSkuDifferentName_stopsByDefault() {
        Product existing = Product.builder().id(1L).sku("SP001").name("Tên cũ")
                .price(BigDecimal.TEN).stockQuantity(0).build();
        when(productRepository.findBySku("SP001")).thenReturn(Optional.of(existing));

        MockMultipartFile file = fileOf(new String[]{"SP001", "", "Tên mới", "", "100000", "", "", ""});
        ProductImportResult result = importService.importFromExcel(file, DEFAULTS);

        assertThat(result.getUpdatedCount()).isZero();
        assertThat(result.getCreatedCount()).isZero();
        assertThat(result.getStoppedAtRow()).isEqualTo(2);
        assertThat(result.getStopReason()).contains("SP001").contains("Tên cũ");
        assertThat(existing.getName()).isEqualTo("Tên cũ");
        verify(productRepository, never()).save(any());
    }

    @Test
    void import_duplicateSkuDifferentName_replacesNameWhenFlagSet() {
        Product existing = Product.builder().id(1L).sku("SP001").name("Tên cũ")
                .price(BigDecimal.TEN).stockQuantity(0).build();
        when(productRepository.findBySku("SP001")).thenReturn(Optional.of(existing));

        ProductImportOptions options = new ProductImportOptions(true, false, false, false, false);
        MockMultipartFile file = fileOf(new String[]{"SP001", "", "Tên mới", "", "100000", "", "", ""});
        ProductImportResult result = importService.importFromExcel(file, options);

        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getStoppedAtRow()).isNull();
        assertThat(existing.getName()).isEqualTo("Tên mới");
    }

    @Test
    void import_duplicateBarcodeDifferentSku_stopsByDefault() {
        Product existing = Product.builder().id(1L).sku("OLD-SKU").barcode("8931234").name("Áo thun")
                .price(BigDecimal.TEN).stockQuantity(0).build();
        when(productRepository.findBySku("NEW-SKU")).thenReturn(Optional.empty());
        when(productRepository.findByBarcode("8931234")).thenReturn(Optional.of(existing));

        MockMultipartFile file = fileOf(new String[]{"NEW-SKU", "8931234", "Áo thun", "", "100000", "", "", ""});
        ProductImportResult result = importService.importFromExcel(file, DEFAULTS);

        assertThat(result.getStoppedAtRow()).isEqualTo(2);
        assertThat(result.getStopReason()).contains("8931234").contains("OLD-SKU");
        assertThat(existing.getSku()).isEqualTo("OLD-SKU");
    }

    @Test
    void import_duplicateBarcodeDifferentSku_replacesSkuWhenFlagSet() {
        Product existing = Product.builder().id(1L).sku("OLD-SKU").barcode("8931234").name("Áo thun")
                .price(BigDecimal.TEN).stockQuantity(0).build();
        when(productRepository.findBySku("NEW-SKU")).thenReturn(Optional.empty());
        when(productRepository.findByBarcode("8931234")).thenReturn(Optional.of(existing));

        ProductImportOptions options = new ProductImportOptions(false, true, false, false, false);
        MockMultipartFile file = fileOf(new String[]{"NEW-SKU", "8931234", "Áo thun", "", "100000", "", "", ""});
        ProductImportResult result = importService.importFromExcel(file, options);

        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(existing.getSku()).isEqualTo("NEW-SKU");
    }

    @Test
    void import_missingRequiredFields_skipsRowButContinues() {
        when(productRepository.findBySku("SP002")).thenReturn(Optional.empty());
        when(productRepository.existsBySlug(any())).thenReturn(false);

        MockMultipartFile file = fileOf(
                new String[]{"", "", "Thiếu mã hàng", "", "100000", "", "", ""},
                new String[]{"SP002", "", "Sản phẩm hợp lệ", "", "100000", "", "", ""});
        ProductImportResult result = importService.importFromExcel(file, DEFAULTS);

        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getNotes()).hasSize(1);
        assertThat(result.getNotes().get(0).getRow()).isEqualTo(2);
    }

    @Test
    void import_unknownCategory_stillCreatesWithNote() {
        when(productRepository.findBySku("SP001")).thenReturn(Optional.empty());
        when(productRepository.existsBySlug(any())).thenReturn(false);
        when(categoryRepository.findByNameIgnoreCase("Không tồn tại")).thenReturn(Optional.empty());

        MockMultipartFile file = fileOf(new String[]{"SP001", "", "Áo thun", "Không tồn tại", "100000", "", "", ""});
        ProductImportResult result = importService.importFromExcel(file, DEFAULTS);

        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getNotes()).anyMatch(n -> n.getMessage().contains("Không tồn tại"));
    }

    @Test
    void import_categoryFound_isAttached() {
        Category category = Category.builder().id(5L).name("Áo").build();
        when(productRepository.findBySku("SP001")).thenReturn(Optional.empty());
        when(productRepository.existsBySlug(any())).thenReturn(false);
        when(categoryRepository.findByNameIgnoreCase("Áo")).thenReturn(Optional.of(category));

        MockMultipartFile file = fileOf(new String[]{"SP001", "", "Áo thun", "Áo", "100000", "", "", ""});
        importService.importFromExcel(file, DEFAULTS);

        verify(productRepository).save(argThatHasCategory(category));
    }

    private Product argThatHasCategory(Category category) {
        return org.mockito.ArgumentMatchers.argThat(p -> p != null && p.getCategories().contains(category));
    }

    @Test
    void import_subscriptionLimitHit_stopsImport() {
        when(productRepository.findBySku("SP001")).thenReturn(Optional.empty());
        doThrow(new SubscriptionRequiredException("Đã đạt giới hạn sản phẩm của gói"))
                .when(subscriptionGuard).requireCanAddProduct(eq(10L), anyLong());

        MockMultipartFile file = fileOf(new String[]{"SP001", "", "Áo thun", "", "100000", "", "", ""});
        ProductImportResult result = importService.importFromExcel(file, DEFAULTS);

        assertThat(result.getCreatedCount()).isZero();
        assertThat(result.getStoppedAtRow()).isEqualTo(2);
        assertThat(result.getStopReason()).contains("giới hạn");
    }
}

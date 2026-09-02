package com.ut.edu.backend.product;

import com.ut.edu.backend.exception.SubscriptionRequiredException;
import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.category.CategoryRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Only covers AdminProductController#createProductVariants (generic
 * free-named-attribute batch generation) - the rest of this controller has
 * no test coverage yet.
 */
@ExtendWith(MockitoExtension.class)
class AdminProductControllerVariantsTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TenantGuard tenantGuard;
    @Mock private SubscriptionGuard subscriptionGuard;
    @Mock private AuthorizationService authorizationService;

    @InjectMocks
    private AdminProductController controller;

    private CreateProductVariantsRequest.VariantRow row(Map<String, String> attributeValues, String sku) {
        CreateProductVariantsRequest.VariantRow row = new CreateProductVariantsRequest.VariantRow();
        row.setAttributeValues(attributeValues);
        row.setSku(sku);
        row.setPrice(new BigDecimal("199000"));
        row.setCostPrice(new BigDecimal("100000"));
        row.setStockQuantity(10);
        return row;
    }

    private Map<String, String> attrs(String name1, String value1, String name2, String value2) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(name1, value1);
        map.put(name2, value2);
        return map;
    }

    private Map<String, String> attrs(String name, String value) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(name, value);
        return map;
    }

    private CreateProductVariantsRequest request(List<String> attributeOrder, List<CreateProductVariantsRequest.VariantRow> rows) {
        CreateProductVariantsRequest request = new CreateProductVariantsRequest();
        request.setName("Áo thun");
        request.setAttributeOrder(attributeOrder);
        request.setVariants(rows);
        return request;
    }

    @Test
    void createProductVariants_success_sharesOneVariantGroupId() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        when(productRepository.countByStoreId(10L)).thenReturn(0L);
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(productRepository.existsBySlug(anyString())).thenReturn(false);
        when(tenantGuard.currentStoreRef()).thenReturn(new Store());
        when(authorizationService.hasRole("OWNER")).thenReturn(true);
        when(productRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<CreateProductVariantsRequest.VariantRow> rows = List.of(
                row(attrs("Màu sắc", "Đen", "Kích cỡ", "S"), "AO-DEN-S"),
                row(attrs("Màu sắc", "Đen", "Kích cỡ", "M"), "AO-DEN-M"));
        ResponseEntity<?> response = controller.createProductVariants(request(List.of("Màu sắc", "Kích cỡ"), rows));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        List<Product> saved = (List<Product>) ((Map<?, ?>) response.getBody()).get("products");
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getVariantGroupId())
                .isNotNull()
                .isEqualTo(saved.get(1).getVariantGroupId());
        assertThat(saved.get(0).getSku()).isEqualTo("AO-DEN-S");
        assertThat(saved.get(1).getSku()).isEqualTo("AO-DEN-M");
        assertThat(saved.get(0).getAttributes()).containsEntry("Màu sắc", "Đen").containsEntry("Kích cỡ", "S");
        assertThat(saved.get(0).getName()).isEqualTo("Áo thun - Đen - S");
    }

    @Test
    void createProductVariants_singleAxis_worksForNonFashionAttribute() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        when(productRepository.countByStoreId(10L)).thenReturn(0L);
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(productRepository.existsBySlug(anyString())).thenReturn(false);
        when(tenantGuard.currentStoreRef()).thenReturn(new Store());
        when(authorizationService.hasRole("OWNER")).thenReturn(true);
        when(productRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<CreateProductVariantsRequest.VariantRow> rows = List.of(
                row(attrs("Hương vị", "Dâu"), "TRA-DAU"),
                row(attrs("Hương vị", "Vani"), "TRA-VANI"));
        ResponseEntity<?> response = controller.createProductVariants(request(List.of("Hương vị"), rows));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        List<Product> saved = (List<Product>) ((Map<?, ?>) response.getBody()).get("products");
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getAttributes()).containsExactly(Map.entry("Hương vị", "Dâu"));
        assertThat(saved.get(0).getName()).isEqualTo("Áo thun - Dâu");
    }

    @Test
    void createProductVariants_threeAxes_generatesCorrectName() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        when(productRepository.countByStoreId(10L)).thenReturn(0L);
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(productRepository.existsBySlug(anyString())).thenReturn(false);
        when(tenantGuard.currentStoreRef()).thenReturn(new Store());
        when(authorizationService.hasRole("OWNER")).thenReturn(true);
        when(productRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> threeAxisValues = new LinkedHashMap<>();
        threeAxisValues.put("Kích cỡ", "M");
        threeAxisValues.put("Màu sắc", "Đen");
        threeAxisValues.put("Chất liệu", "Cotton");
        List<CreateProductVariantsRequest.VariantRow> rows = List.of(row(threeAxisValues, "AO-1"));
        ResponseEntity<?> response = controller.createProductVariants(
                request(List.of("Kích cỡ", "Màu sắc", "Chất liệu"), rows));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        List<Product> saved = (List<Product>) ((Map<?, ?>) response.getBody()).get("products");
        assertThat(saved.get(0).getName()).isEqualTo("Áo thun - M - Đen - Cotton");
    }

    @Test
    void createProductVariants_managerCall_stripsTaxRate() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        when(productRepository.countByStoreId(10L)).thenReturn(0L);
        when(productRepository.existsBySku(anyString())).thenReturn(false);
        when(productRepository.existsBySlug(anyString())).thenReturn(false);
        when(tenantGuard.currentStoreRef()).thenReturn(new Store());
        when(authorizationService.hasRole("OWNER")).thenReturn(false);
        when(productRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CreateProductVariantsRequest request = request(List.of("Kích cỡ"),
                List.of(row(attrs("Kích cỡ", "L"), "AO-TRANG-L")));
        request.setTaxRate(new BigDecimal("8"));

        ResponseEntity<?> response = controller.createProductVariants(request);

        @SuppressWarnings("unchecked")
        List<Product> saved = (List<Product>) ((Map<?, ?>) response.getBody()).get("products");
        assertThat(saved.get(0).getTaxRate()).isNull();
    }

    @Test
    void createProductVariants_batchExceedsLimit_returns402AndPersistsNothing() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        when(productRepository.countByStoreId(10L)).thenReturn(48L);
        doThrow(new SubscriptionRequiredException(
                "Your BASIC plan allows up to 50 products. Adding 3 more would exceed the limit. Upgrade to add more."))
                .when(subscriptionGuard).requireCanAddProducts(10L, 48L, 3);

        List<CreateProductVariantsRequest.VariantRow> rows = List.of(
                row(attrs("Kích cỡ", "S"), "SKU1"), row(attrs("Kích cỡ", "M"), "SKU2"), row(attrs("Kích cỡ", "L"), "SKU3"));
        ResponseEntity<?> response = controller.createProductVariants(request(List.of("Kích cỡ"), rows));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        verify(productRepository, never()).saveAll(anyList());
    }

    @Test
    void createProductVariants_duplicateComboInBatch_returns400() {
        when(tenantGuard.requireStore()).thenReturn(10L);

        List<CreateProductVariantsRequest.VariantRow> rows = List.of(
                row(attrs("Kích cỡ", "S"), "SKU1"), row(attrs("Kích cỡ", "S"), "SKU2"));
        ResponseEntity<?> response = controller.createProductVariants(request(List.of("Kích cỡ"), rows));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(productRepository, never()).saveAll(anyList());
    }

    @Test
    void createProductVariants_duplicateSkuInBatch_returns400() {
        when(tenantGuard.requireStore()).thenReturn(10L);

        List<CreateProductVariantsRequest.VariantRow> rows = List.of(
                row(attrs("Kích cỡ", "S"), "SAME-SKU"), row(attrs("Kích cỡ", "M"), "SAME-SKU"));
        ResponseEntity<?> response = controller.createProductVariants(request(List.of("Kích cỡ"), rows));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(productRepository, never()).saveAll(anyList());
    }

    @Test
    void createProductVariants_rowAttributeKeysDontMatchAttributeOrder_returns400() {
        when(tenantGuard.requireStore()).thenReturn(10L);

        // declares "Kích cỡ" but the row is keyed by "Màu sắc" instead
        List<CreateProductVariantsRequest.VariantRow> rows = List.of(row(attrs("Màu sắc", "Đen"), "SKU1"));
        ResponseEntity<?> response = controller.createProductVariants(request(List.of("Kích cỡ"), rows));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(productRepository, never()).saveAll(anyList());
    }
}

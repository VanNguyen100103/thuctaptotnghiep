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
 * Only covers AdminProductController#createProductVariants (Color x Size
 * batch generation) - the rest of this controller has no test coverage yet.
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

    private CreateProductVariantsRequest.VariantRow row(String color, String size, String sku) {
        CreateProductVariantsRequest.VariantRow row = new CreateProductVariantsRequest.VariantRow();
        row.setColor(color);
        row.setSize(size);
        row.setSku(sku);
        row.setPrice(new BigDecimal("199000"));
        row.setCostPrice(new BigDecimal("100000"));
        row.setStockQuantity(10);
        return row;
    }

    private CreateProductVariantsRequest request(List<CreateProductVariantsRequest.VariantRow> rows) {
        CreateProductVariantsRequest request = new CreateProductVariantsRequest();
        request.setName("Áo thun");
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
                row("Đen", "S", "AO-DEN-S"),
                row("Đen", "M", "AO-DEN-M"));
        ResponseEntity<?> response = controller.createProductVariants(request(rows));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        List<Product> saved = (List<Product>) ((Map<?, ?>) response.getBody()).get("products");
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getVariantGroupId())
                .isNotNull()
                .isEqualTo(saved.get(1).getVariantGroupId());
        assertThat(saved.get(0).getSku()).isEqualTo("AO-DEN-S");
        assertThat(saved.get(1).getSku()).isEqualTo("AO-DEN-M");
        assertThat(saved.get(0).getAvailableColors()).containsExactly("Đen");
        assertThat(saved.get(0).getAvailableSizes()).containsExactly("S");
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

        CreateProductVariantsRequest request = request(List.of(row("Trắng", "L", "AO-TRANG-L")));
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
                row("Đen", "S", "SKU1"), row("Đen", "M", "SKU2"), row("Đen", "L", "SKU3"));
        ResponseEntity<?> response = controller.createProductVariants(request(rows));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        verify(productRepository, never()).saveAll(anyList());
    }

    @Test
    void createProductVariants_duplicateComboInBatch_returns400() {
        when(tenantGuard.requireStore()).thenReturn(10L);

        List<CreateProductVariantsRequest.VariantRow> rows = List.of(
                row("Đen", "S", "SKU1"), row("Đen", "S", "SKU2"));
        ResponseEntity<?> response = controller.createProductVariants(request(rows));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(productRepository, never()).saveAll(anyList());
    }

    @Test
    void createProductVariants_duplicateSkuInBatch_returns400() {
        when(tenantGuard.requireStore()).thenReturn(10L);

        List<CreateProductVariantsRequest.VariantRow> rows = List.of(
                row("Đen", "S", "SAME-SKU"), row("Trắng", "M", "SAME-SKU"));
        ResponseEntity<?> response = controller.createProductVariants(request(rows));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(productRepository, never()).saveAll(anyList());
    }
}

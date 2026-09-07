package com.ut.edu.backend.product;

import com.ut.edu.backend.category.Category;
import com.ut.edu.backend.category.CategoryRepository;
import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the three "Khác" bulk-action endpoints added for the product
 * list's checkbox selection (bulk-status / bulk-delete / bulk-categories),
 * matching KiotViet's "Ngừng kinh doanh" / "Xóa" / "Đổi nhóm hàng" menu
 * items. A product id missing or belonging to another store is reported in
 * the response's "errors" list rather than failing the whole batch, same
 * as the pre-existing bulkPriceUpdate endpoint.
 */
@ExtendWith(MockitoExtension.class)
class AdminProductControllerBulkActionsTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TenantGuard tenantGuard;
    @Mock private SubscriptionGuard subscriptionGuard;
    @Mock private AuthorizationService authorizationService;
    @Mock private RedisProductCacheService productCacheService;

    @InjectMocks
    private AdminProductController controller;

    private Store store(Long id) {
        Store s = new Store();
        s.setId(id);
        return s;
    }

    private Product product(Long id, Store store) {
        Product p = new Product();
        p.setId(id);
        p.setStore(store);
        p.setActive(true);
        return p;
    }

    @Test
    void bulkUpdateStatus_deactivatesFoundProducts_reportsMissingAsError() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        Store myStore = store(10L);
        Product p1 = product(1L, myStore);
        Product p2 = product(2L, myStore);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(productRepository.findById(2L)).thenReturn(Optional.of(p2));
        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        when(tenantGuard.isCurrentStore(myStore)).thenReturn(true);

        Map<String, Object> body = Map.of("productIds", List.of(1, 2, 99), "active", false);
        ResponseEntity<?> response = controller.bulkUpdateStatus(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> resBody = (Map<?, ?>) response.getBody();
        assertThat(resBody.get("updatedCount")).isEqualTo(2);
        assertThat(resBody.get("totalRequested")).isEqualTo(3);
        assertThat((List<?>) resBody.get("errors")).hasSize(1);
        assertThat(p1.getActive()).isFalse();
        assertThat(p2.getActive()).isFalse();
        verify(productRepository, times(2)).save(any());
        verify(productCacheService).invalidateAllSearchResults();
    }

    @Test
    void bulkUpdateStatus_missingActiveField_returnsBadRequest() {
        Map<String, Object> body = Map.of("productIds", List.of(1));
        ResponseEntity<?> response = controller.bulkUpdateStatus(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(productRepository, never()).save(any());
    }

    @Test
    void bulkUpdateStatus_emptyProductIds_returnsBadRequest() {
        Map<String, Object> body = Map.of("productIds", List.of(), "active", true);
        ResponseEntity<?> response = controller.bulkUpdateStatus(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void bulkDeleteProducts_softDeletesEachAndInvalidatesCacheOnce() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        Store myStore = store(10L);
        Product p1 = product(1L, myStore);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(tenantGuard.isCurrentStore(myStore)).thenReturn(true);

        Map<String, Object> body = Map.of("productIds", List.of(1));
        ResponseEntity<?> response = controller.bulkDeleteProducts(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> resBody = (Map<?, ?>) response.getBody();
        assertThat(resBody.get("deletedCount")).isEqualTo(1);
        assertThat(p1.getActive()).isFalse();
        verify(productCacheService, times(1)).invalidateAllSearchResults();
    }

    @Test
    void bulkDeleteProducts_crossTenantId_isSkippedNotDeleted() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        Store otherStore = store(20L);
        Product foreign = product(5L, otherStore);
        when(productRepository.findById(5L)).thenReturn(Optional.of(foreign));
        when(tenantGuard.isCurrentStore(otherStore)).thenReturn(false);

        Map<String, Object> body = Map.of("productIds", List.of(5));
        ResponseEntity<?> response = controller.bulkDeleteProducts(body);

        Map<?, ?> resBody = (Map<?, ?>) response.getBody();
        assertThat(resBody.get("deletedCount")).isEqualTo(0);
        assertThat((List<?>) resBody.get("errors")).hasSize(1);
        assertThat(foreign.getActive()).isTrue();
        verify(productCacheService, never()).invalidateAllSearchResults();
    }

    @Test
    void bulkUpdateCategories_replacesCategoriesOnEachProduct() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        Store myStore = store(10L);
        Product p1 = product(1L, myStore);
        p1.setCategories(new java.util.HashSet<>(Set.of(existingCategory(7L))));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(tenantGuard.isCurrentStore(myStore)).thenReturn(true);

        Category target = existingCategory(3L);
        when(categoryRepository.findAllById(List.of(3L))).thenReturn(List.of(target));

        Map<String, Object> body = Map.of("productIds", List.of(1), "categoryIds", List.of(3));
        ResponseEntity<?> response = controller.bulkUpdateCategories(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> resBody = (Map<?, ?>) response.getBody();
        assertThat(resBody.get("updatedCount")).isEqualTo(1);
        assertThat(p1.getCategories()).containsExactly(target);
    }

    @Test
    void bulkUpdateCategories_unknownCategoryId_returnsBadRequestWithoutTouchingProducts() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        when(categoryRepository.findAllById(List.of(404L))).thenReturn(List.of());

        Map<String, Object> body = Map.of("productIds", List.of(1), "categoryIds", List.of(404));
        ResponseEntity<?> response = controller.bulkUpdateCategories(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(productRepository, never()).findById(anyLong());
        verify(productRepository, never()).save(any());
    }

    private Category existingCategory(Long id) {
        Category c = new Category();
        c.setId(id);
        c.setName("Nhóm " + id);
        c.setSlug("nhom-" + id);
        return c;
    }
}

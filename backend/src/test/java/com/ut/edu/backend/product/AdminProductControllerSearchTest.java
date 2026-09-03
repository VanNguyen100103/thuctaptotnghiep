package com.ut.edu.backend.product;

import com.ut.edu.backend.category.CategoryRepository;
import com.ut.edu.backend.order.OrderRepository;
import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Covers AdminProductController#searchProducts' sortBy -> DB column mapping.
 * adminSearchProducts is a native query, so a Sort built straight from the
 * frontend's camelCase entity field names (e.g. "createdAt") gets inserted
 * into the SQL as a literal, nonexistent column ("createdat") and blows up -
 * this is what the controller's sortBy handling must translate around.
 */
@ExtendWith(MockitoExtension.class)
class AdminProductControllerSearchTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private TenantGuard tenantGuard;
    @Mock private SubscriptionGuard subscriptionGuard;
    @Mock private AuthorizationService authorizationService;

    @InjectMocks
    private AdminProductController controller;

    @SuppressWarnings("unchecked")
    private Sort capturedSort() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verifyAdminSearchCalledWith(pageableCaptor);
        return pageableCaptor.getValue().getSort();
    }

    private void verifyAdminSearchCalledWith(ArgumentCaptor<Pageable> pageableCaptor) {
        org.mockito.Mockito.verify(productRepository)
                .adminSearchProducts(any(), anyLong(), pageableCaptor.capture());
    }

    @Test
    void searchProducts_defaultSort_mapsCreatedAtToSnakeCaseColumn() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        when(productRepository.adminSearchProducts(any(), eq(10L), any())).thenReturn(Page.empty());

        controller.searchProducts("ao", 0, 20, "createdAt", "DESC");

        Sort.Order order = capturedSort().getOrderFor("created_at");
        assertThat(order).isNotNull();
        assertThat(order.isDescending()).isTrue();
    }

    @Test
    void searchProducts_stockQuantitySort_mapsToSnakeCaseColumn() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        when(productRepository.adminSearchProducts(any(), eq(10L), any())).thenReturn(Page.empty());

        controller.searchProducts("ao", 0, 20, "stockQuantity", "ASC");

        Sort.Order order = capturedSort().getOrderFor("stock_quantity");
        assertThat(order).isNotNull();
        assertThat(order.isAscending()).isTrue();
    }

    @Test
    void searchProducts_singleWordSort_passesThroughUnchanged() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        when(productRepository.adminSearchProducts(any(), eq(10L), any())).thenReturn(Page.empty());

        controller.searchProducts("ao", 0, 20, "price", "DESC");

        assertThat(capturedSort().getOrderFor("price")).isNotNull();
    }

    @Test
    void searchProducts_unrecognizedSortBy_fallsBackToCreatedAtColumn() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        when(productRepository.adminSearchProducts(any(), eq(10L), any())).thenReturn(Page.empty());

        controller.searchProducts("ao", 0, 20, "somethingUnexpected", "DESC");

        assertThat(capturedSort().getOrderFor("created_at")).isNotNull();
    }
}

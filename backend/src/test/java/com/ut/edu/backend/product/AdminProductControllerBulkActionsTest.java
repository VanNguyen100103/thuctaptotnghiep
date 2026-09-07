package com.ut.edu.backend.product;

import com.ut.edu.backend.cart.CartItemRepository;
import com.ut.edu.backend.category.Category;
import com.ut.edu.backend.category.CategoryRepository;
import com.ut.edu.backend.order.OrderRepository;
import com.ut.edu.backend.purchaseorder.PurchaseOrderRepository;
import com.ut.edu.backend.sale.SaleRepository;
import com.ut.edu.backend.security.AuthorizationService;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.SubscriptionGuard;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.wishlist.WishlistRepository;

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
    @Mock private OrderRepository orderRepository;
    @Mock private SaleRepository saleRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private WishlistRepository wishlistRepository;
    @Mock private ProductViewRepository productViewRepository;

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
    void bulkDeleteProducts_reallyDeletesRowsAndReleasesEveryReference() {
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
        // A real delete, not a deactivate - the row goes, the flag is untouched.
        verify(productRepository).deleteAllById(List.of(1L));
        verify(productRepository, never()).save(any());
        assertThat(p1.getActive()).isTrue();
        // Throwaway state is deleted outright...
        verify(cartItemRepository).deleteByProductIdIn(List.of(1L));
        verify(wishlistRepository).deleteByProductIdIn(List.of(1L));
        verify(productViewRepository).deleteByProductIdIn(List.of(1L));
        // ...while transaction history is kept and merely unlinked.
        verify(orderRepository).detachProducts(List.of(1L));
        verify(saleRepository).detachProducts(List.of(1L));
        verify(purchaseOrderRepository).detachProducts(List.of(1L));
        verify(productCacheService, times(1)).invalidateAllSearchResults();
    }

    @Test
    void bulkDeleteProducts_deletesEvenWhenTheProductHasBeenSold() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        Store myStore = store(10L);
        Product sold = product(1L, myStore);
        Product unsold = product(2L, myStore);
        when(productRepository.findById(1L)).thenReturn(Optional.of(sold));
        when(productRepository.findById(2L)).thenReturn(Optional.of(unsold));
        when(tenantGuard.isCurrentStore(myStore)).thenReturn(true);

        Map<String, Object> body = Map.of("productIds", List.of(1, 2));
        ResponseEntity<?> response = controller.bulkDeleteProducts(body);

        Map<?, ?> resBody = (Map<?, ?>) response.getBody();
        // Having order history no longer keeps a product alive - the order
        // line survives on its own snapshot instead.
        assertThat(resBody.get("deletedCount")).isEqualTo(2);
        verify(orderRepository).detachProducts(List.of(1L, 2L));
        verify(productRepository).deleteAllById(List.of(1L, 2L));
    }

    @Test
    void bulkDeleteProducts_crossTenantId_isSkippedAndNothingIsDeleted() {
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
        verify(productRepository, never()).deleteAllById(any());
        verify(orderRepository, never()).detachProducts(any());
        verify(productCacheService, never()).invalidateAllSearchResults();
    }

    @Test
    void deleteProduct_singleRemovesTheRowAndUnlinksHistory() {
        when(tenantGuard.requireStore()).thenReturn(10L);
        Store myStore = store(10L);
        Product sold = product(1L, myStore);
        when(productRepository.findById(1L)).thenReturn(Optional.of(sold));
        when(tenantGuard.isCurrentStore(myStore)).thenReturn(true);

        ResponseEntity<?> response = controller.deleteProduct(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(productRepository).delete(sold);
        verify(productRepository, never()).save(any());
        verify(cartItemRepository).deleteByProductIdIn(List.of(1L));
        verify(orderRepository).detachProducts(List.of(1L));
        assertThat(sold.getActive()).isTrue();
    }

    private Category existingCategory(Long id) {
        Category c = new Category();
        c.setId(id);
        c.setName("Nhóm " + id);
        c.setSlug("nhom-" + id);
        return c;
    }
}

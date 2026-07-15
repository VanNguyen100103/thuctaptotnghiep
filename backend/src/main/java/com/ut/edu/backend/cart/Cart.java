package com.ut.edu.backend.cart;

import com.ut.edu.backend.user.User;
import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Shopping cart entity
 * Supports Redis caching for performance
 */
@Entity
@Table(name = "carts", indexes = {
    @Index(name = "idx_carts_store", columnList = "store_id")
}, uniqueConstraints = {
    // one cart per user per store (relation becomes ManyToOne in the 1.4 refactor)
    @UniqueConstraint(name = "uk_carts_store_user", columnNames = {"user_id", "store_id"})
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store", "user", "items"})
public class Cart extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tenant link: the store this cart belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<CartItem> items = new HashSet<>();

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    // Helper methods
    public void addItem(CartItem item) {
        items.add(item);
        item.setCart(this);
    }

    public void removeItem(CartItem item) {
        items.remove(item);
        item.setCart(null);
    }

    public void clearCart() {
        items.clear();
    }

    public Integer getTotalItems() {
        return items.stream()
                   .mapToInt(CartItem::getQuantity)
                   .sum();
    }

    public BigDecimal getTotalPrice() {
        return items.stream()
                   .map(CartItem::getSubtotal)
                   .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

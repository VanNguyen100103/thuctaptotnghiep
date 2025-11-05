package com.ut.edu.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Shopping cart entity
 * Supports Redis caching for performance
 */
@Entity
@Table(name = "carts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"user", "items"})
public class Cart extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
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

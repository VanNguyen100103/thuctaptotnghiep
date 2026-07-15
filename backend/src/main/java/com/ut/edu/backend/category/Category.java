package com.ut.edu.backend.category;

import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.util.HashSet;
import java.util.Set;

/**
 * Category entity for product categorization
 * Supports hierarchical categories (parent-child relationship)
 */
@Entity
@Table(name = "categories", indexes = {
    @Index(name = "idx_category_name", columnList = "name"),
    @Index(name = "idx_category_slug", columnList = "slug"),
    @Index(name = "idx_categories_store", columnList = "store_id")
}, uniqueConstraints = {
    // name/slug are unique per store, not globally (multi-tenant)
    @UniqueConstraint(name = "uk_categories_store_name", columnNames = {"store_id", "name"}),
    @UniqueConstraint(name = "uk_categories_store_slug", columnNames = {"store_id", "slug"})
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store", "parent", "children", "products"})
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tenant link: the store this category belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    @NotBlank(message = "Category name is required")
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank(message = "Category slug is required")
    @Column(nullable = false, length = 100)
    private String slug;

    @Column(length = 500)
    private String description;

    private String imageUrl;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    // Hierarchical structure
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @JsonIgnore
    private Category parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<Category> children = new HashSet<>();

    @ManyToMany(mappedBy = "categories")
    @JsonIgnore
    @Builder.Default
    private Set<Product> products = new HashSet<>();

    // Helper methods
    public void addChild(Category child) {
        children.add(child);
        child.setParent(this);
    }

    public void removeChild(Category child) {
        children.remove(child);
        child.setParent(null);
    }
}

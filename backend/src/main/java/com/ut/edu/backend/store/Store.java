package com.ut.edu.backend.store;

import com.ut.edu.backend.common.BaseEntity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Store entity - the tenant root of the multi-tenant SaaS model.
 * Every business entity is scoped to a store via store_id.
 */
@Entity
@Table(name = "stores", indexes = {
    @Index(name = "idx_store_slug", columnList = "slug")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class Store extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Store name is required")
    @Size(min = 2, max = 100, message = "Store name must be between 2 and 100 characters")
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * URL-safe unique identifier, used for the public storefront:
     * /api/stores/{slug}/products
     */
    @NotBlank(message = "Store slug is required")
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug must be lowercase letters, digits and hyphens")
    @Size(min = 2, max = 100)
    @Column(unique = true, nullable = false, length = 100)
    private String slug;

    @Column(length = 500)
    private String logoUrl;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StoreStatus status = StoreStatus.TRIAL;
}

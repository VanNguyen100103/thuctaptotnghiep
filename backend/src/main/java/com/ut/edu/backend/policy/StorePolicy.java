package com.ut.edu.backend.policy;

import com.ut.edu.backend.common.BaseEntity;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.Filter;

/**
 * "Chính sách cửa hàng" - a free-named policy entry (đổi trả, vận chuyển,
 * bảo hành, đặt bàn, ...) a store owner writes for their own store. Read by
 * the storefront AI chat (see com.ut.edu.backend.ai.ChatToolExecutor) so it
 * can answer customers using the store's real policy text.
 */
@Entity
@Table(name = "store_policies", indexes = {
    @Index(name = "idx_store_policies_store", columnList = "store_id")
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, exclude = {"store"})
public class StorePolicy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    /** Free-named, e.g. "Chính sách đổi trả", "Chính sách đặt bàn" - no fixed type enum, same philosophy as Product#attributes. */
    @NotBlank(message = "Policy title is required")
    @Column(nullable = false, length = 200)
    private String title;

    @NotBlank(message = "Policy content is required")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}

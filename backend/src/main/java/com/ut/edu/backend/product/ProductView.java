package com.ut.edu.backend.product;

import com.ut.edu.backend.user.User;
import com.ut.edu.backend.store.Store;
import com.ut.edu.backend.store.TenantContext;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * ProductView entity for tracking product view history
 * Supports both authenticated users and anonymous sessions
 */
@Entity
@Table(name = "product_views", indexes = {
    @Index(name = "idx_product_view_user", columnList = "user_id"),
    @Index(name = "idx_product_view_product", columnList = "product_id"),
    @Index(name = "idx_product_view_session", columnList = "session_id"),
    @Index(name = "idx_product_view_viewed_at", columnList = "viewed_at"),
    @Index(name = "idx_user_product_composite", columnList = "user_id,product_id"),
    @Index(name = "idx_session_product_composite", columnList = "session_id,product_id"),
    @Index(name = "idx_product_views_store", columnList = "store_id")
})
@Filter(name = TenantContext.TENANT_FILTER, condition = "store_id = :storeId")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tenant link: the store whose product was viewed
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @JsonIgnore
    private Store store;

    /**
     * User who viewed the product (null for anonymous users)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * Product that was viewed
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Session ID for anonymous users (null for authenticated users)
     */
    @Column(name = "session_id", length = 100)
    private String sessionId;

    /**
     * Timestamp when the product was viewed
     */
    @Column(name = "viewed_at", nullable = false)
    @Builder.Default
    private LocalDateTime viewedAt = LocalDateTime.now();

    /**
     * User's IP address at the time of viewing (for analytics/fraud detection)
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User agent string (for analytics)
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        if (viewedAt == null) {
            viewedAt = LocalDateTime.now();
        }
    }

    /**
     * Check if this view belongs to an authenticated user
     */
    public boolean isAuthenticatedView() {
        return user != null;
    }

    /**
     * Check if this view belongs to an anonymous session
     */
    public boolean isAnonymousView() {
        return user == null && sessionId != null;
    }
}

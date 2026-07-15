package com.ut.edu.backend.common;

import com.ut.edu.backend.store.TenantContext;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base entity class with common fields for all entities
 * Provides automatic auditing of creation and modification timestamps
 *
 * Also registers the global tenant filter definition: entities carrying a
 * store_id declare @Filter(name = TenantContext.TENANT_FILTER, ...) and the
 * filter is enabled per request by TenantFilterAspect when a tenant is bound.
 */
@FilterDef(name = TenantContext.TENANT_FILTER,
           parameters = @ParamDef(name = TenantContext.PARAM_STORE_ID, type = Long.class))
@Data
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity implements Serializable {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

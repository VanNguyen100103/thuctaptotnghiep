package com.ut.edu.backend.store;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Enables the Hibernate tenant filter before every repository call when a
 * tenant is bound to the request (see {@link TenantResolverFilter}), so every
 * query on entities carrying store_id is transparently scoped to that store.
 *
 * Two known gaps, covered by {@link TenantGuard} in the service layer
 * (defense in depth): em.find() by primary key bypasses Hibernate filters,
 * and non-HTTP threads (Kafka consumers, @Scheduled jobs) never have a
 * TenantContext, so the filter stays off there by design.
 */
@Aspect
@Component
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(public * org.springframework.data.repository.Repository+.*(..))")
    public void enableTenantFilter() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            return;
        }
        // Safe to unwrap here: TenantContext is only set on HTTP threads,
        // where OSIV (spring.jpa.open-in-view) has already bound a session.
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter(TenantContext.TENANT_FILTER)
               .setParameter(TenantContext.PARAM_STORE_ID, storeId);
    }
}

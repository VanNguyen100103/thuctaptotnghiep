package com.ut.edu.backend.store;

import com.ut.edu.backend.security.UserPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the tenant (storeId) of the current request into {@link TenantContext}.
 *
 * Resolution order:
 * 1. Public storefront URL: /stores/{slug}/** - the slug wins, so a staff
 *    member browsing another store's storefront sees that store, not their own.
 * 2. Authenticated owner/manager/staff: storeId carried by the JWT
 *    (exposed on {@link UserPrincipal}, claim "storeId").
 *
 * When neither applies (customer on legacy routes, platform admin, webhooks)
 * the context stays empty and the Hibernate tenant filter is not enabled.
 * Registered in SecurityConfig right after JwtAuthenticationFilter; the
 * context is always cleared in finally so pooled threads never leak a tenant.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantResolverFilter extends OncePerRequestFilter {

    /** Matches /stores/{slug} and /stores/{slug}/... (context path already stripped). */
    private static final Pattern STOREFRONT_PATH = Pattern.compile("^/stores/([a-z0-9]+(?:-[a-z0-9]+)*)(?:/.*)?$");

    private final StoreRepository storeRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Long storeId = resolveFromSlug(request);
            if (storeId == null) {
                storeId = resolveFromAuthentication();
            }
            if (storeId != null) {
                TenantContext.setStoreId(storeId);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Long resolveFromSlug(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Matcher matcher = STOREFRONT_PATH.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        // Suspended stores resolve to "no tenant" -> storefront naturally 404s
        return storeRepository.findBySlugAndStatusNot(matcher.group(1), StoreStatus.SUSPENDED)
                .map(Store::getId)
                .orElse(null);
    }

    private Long resolveFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getStoreId();
        }
        return null;
    }
}

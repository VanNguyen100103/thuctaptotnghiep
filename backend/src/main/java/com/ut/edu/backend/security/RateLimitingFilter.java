package com.ut.edu.backend.security;

import com.ut.edu.backend.order.Order;

import com.ut.edu.backend.config.RateLimitingConfig;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rate Limiting Filter
 * Applies rate limits to API endpoints based on IP address
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingConfig rateLimitingConfig;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String ip = getClientIP(request);
        String path = request.getRequestURI();

        // Determine which bucket to use based on endpoint
        Bucket bucket = selectBucket(ip, path);

        // Try to consume 1 token from the bucket
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // Request allowed - add rate limit headers
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;

            log.warn("Rate limit exceeded for IP: {} on endpoint: {}. Retry after {} seconds",
                    ip, path, waitForRefill);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));

            String jsonResponse = String.format(
                    "{\"error\":\"Too many requests\",\"message\":\"Rate limit exceeded. Please try again in %d seconds.\",\"retryAfter\":%d}",
                    waitForRefill, waitForRefill
            );
            response.getWriter().write(jsonResponse);
        }
    }

    /**
     * Select appropriate bucket based on endpoint
     */
    private Bucket selectBucket(String ip, String path) {
        // Authentication endpoints - strict rate limiting
        if (path.contains("/api/auth/login")) {
            return rateLimitingConfig.resolveAuthBucket(ip);
        }

        // Registration endpoint - very strict
        if (path.contains("/api/auth/register")) {
            return rateLimitingConfig.resolveRegistrationBucket(ip);
        }

        // Password reset endpoint - very strict
        if (path.contains("/api/auth/forgot-password") || path.contains("/api/auth/reset-password")) {
            return rateLimitingConfig.resolvePasswordResetBucket(ip);
        }

        // Search endpoints
        if (path.contains("/api/products/search") || path.contains("/api/ai/search")) {
            return rateLimitingConfig.resolveSearchBucket(ip);
        }

        // Order endpoints
        if (path.contains("/api/orders")) {
            return rateLimitingConfig.resolveOrderBucket(ip);
        }

        // Default rate limit for all other endpoints
        return rateLimitingConfig.resolveBucket(ip);
    }

    /**
     * Get client IP address, considering proxy headers
     */
    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xfHeader.split(",")[0].trim();
        }

        String xrealHeader = request.getHeader("X-Real-IP");
        if (xrealHeader != null && !xrealHeader.isEmpty()) {
            return xrealHeader;
        }

        return request.getRemoteAddr();
    }

    /**
     * Skip rate limiting for certain paths (e.g., health checks, static resources)
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // getRequestURI() includes the context path (/api on prod), so strip it
        // before matching — otherwise none of these prefixes ever match there
        String path = request.getRequestURI().substring(request.getContextPath().length());

        // Skip rate limiting for:
        // - Health check endpoints
        // - API documentation (Swagger/OpenAPI)
        // - Static resources
        // - WebSocket connections
        return path.startsWith("/actuator/health") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/static/") ||
                path.startsWith("/ws/");
    }
}

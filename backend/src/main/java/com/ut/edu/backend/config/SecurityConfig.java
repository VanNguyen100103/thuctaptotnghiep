package com.ut.edu.backend.config;

import com.ut.edu.backend.security.CustomUserDetailsService;
import com.ut.edu.backend.security.JwtAuthenticationEntryPoint;
import com.ut.edu.backend.security.JwtAuthenticationFilter;
import com.ut.edu.backend.security.RateLimitingFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Security Configuration
 * Implements OWASP Top 10 security best practices:
 * 1. Authentication & Access Control
 * 2. Cryptographic Failures (BCrypt password encoding)
 * 3. Injection (Parameterized queries via JPA)
 * 4. Insecure Design (Secure defaults)
 * 5. Security Misconfiguration (Secure headers, HTTPS)
 * 6. Vulnerable Components (Up-to-date dependencies)
 * 7. Authentication Failures (Account lockout, JWT expiration)
 * 8. Software & Data Integrity (CSRF protection)
 * 9. Security Logging (Audit logging enabled)
 * 10. Server-Side Request Forgery (Input validation)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(
    securedEnabled = true,
    jsr250Enabled = true,
    prePostEnabled = true
)
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with strength 12 for strong password hashing
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer webSecurityCustomizer() {
        // Completely bypass security for actuator endpoints
        return (web) -> web.ignoring().requestMatchers("/actuator/**");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CORS Configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource))

            // CSRF Protection (OWASP A08:2021 – Software and Data Integrity Failures)
            // Enabled with cookie-based token repository
            // Public endpoints (login, register) are excluded from CSRF protection
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(
                    "/auth/login",
                    "/auth/register",
                    "/auth/refresh",
                    "/auth/verify-otp",
                    "/auth/resend-verification",
                    "/auth/forgot-password",
                    "/auth/reset-password",
                    "/auth/check-username",
                    "/auth/check-email",
                    "/2fa/**",  // 2FA endpoints don't need CSRF
                    "/payments/webhook/**",
                    "/addresses/**",  // JWT-authenticated endpoints don't need CSRF
                    "/orders/**",
                    "/cart/**",
                    "/payments/**",
                    "/reviews/**",
                    "/wishlist/**",
                    "/coupons/**",
                    "/views/**"  // View tracking endpoints
                )
            )

            // Exception Handling
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            )

            // Session Management - Stateless (JWT-based)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Authorization Rules
            // Note: context-path is /api, so Spring Security sees paths WITHOUT /api prefix
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - Auth related (no authentication required)
                .requestMatchers(
                    "/auth/login",
                    "/auth/register",
                    "/auth/refresh",
                    "/auth/verify-otp",
                    "/auth/resend-verification",
                    "/auth/forgot-password",
                    "/auth/reset-password",
                    "/auth/check-username",
                    "/auth/check-email",
                    "/auth/csrf-token"
                ).permitAll()

                // Public endpoints - Resources (GET only)
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                    "/products/**",
                    "/categories/**",
                    "/reviews/**",
                    "/views/recently-viewed/session/**"  // Anonymous user recently viewed
                ).permitAll()

                // View tracking endpoints (both GET and POST)
                .requestMatchers(
                    "/views/track"  // Track view for both authenticated and anonymous
                ).permitAll()

                // Documentation & Health
                .requestMatchers(
                    "/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/actuator/**",
                    "/error"
                ).permitAll()

                // PayPal webhook
                .requestMatchers("/payments/webhook/**").permitAll()

                // Admin endpoints
                .requestMatchers("/admin/**").hasRole("ADMIN")

                // All other endpoints require authentication
                .anyRequest().authenticated()
            )

            // Security Headers (OWASP A05:2021 – Security Misconfiguration)
            .headers(headers -> headers
                // Prevent Clickjacking
                .frameOptions(frame -> frame.deny())

                // XSS Protection - disabled in favor of CSP
                .xssProtection(xss -> xss.disable())

                // Content Type Options - Prevent MIME sniffing
                .contentTypeOptions(content -> {})  // Enable X-Content-Type-Options: nosniff

                // HTTP Strict Transport Security (HSTS)
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )

                // Content Security Policy - Strict XSS Protection
                // Removed 'unsafe-inline' to prevent XSS attacks
                // All inline scripts and styles must be moved to external files
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; " +
                                    "script-src 'self'; " +  // ✅ No unsafe-inline - blocks XSS
                                    "style-src 'self'; " +   // ✅ No unsafe-inline - blocks style injection
                                    "img-src 'self' data: https:; " +
                                    "font-src 'self' data:; " +
                                    "connect-src 'self'; " +
                                    "frame-ancestors 'none'; " +  // Additional clickjacking protection
                                    "base-uri 'self'; " +  // Prevent base tag injection
                                    "form-action 'self'")  // Prevent form hijacking
                )

                // Referrer Policy
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )

                // Permissions Policy (formerly Feature Policy)
                .permissionsPolicy(permissions -> permissions
                    .policy("geolocation=(), microphone=(), camera=()")
                )
            );

        // Add Rate Limiting filter (before authentication)
        http.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);

        // Add JWT authentication filter
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

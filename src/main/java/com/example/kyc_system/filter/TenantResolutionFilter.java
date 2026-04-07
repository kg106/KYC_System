package com.example.kyc_system.filter;

import com.example.kyc_system.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Servlet filter that resolves and validates the tenant for every request.
 * Runs AFTER JwtAuthenticationFilter (Order 3), so authentication is already available.
 *
 * Tenant resolution priority:
 * 1. JWT claim "tenantId" (from authenticated user's token)
 * 2. X-Tenant-ID request header (for public endpoints)
 *
 * Super admins bypass tenant scoping entirely.
 * Always clears TenantContext in the finally block to prevent thread pool leaks.
 */
@Component
@Order(3) // Runs after JwtAuthenticationFilter
@RequiredArgsConstructor
@Slf4j
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final String MDC_TENANT_KEY = "tenantId";

    // These paths don't need tenant context (public endpoints)
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/swagger-ui/",
            "/v3/api-docs",
            "/swagger-ui",
            "/actuator",
            "/error");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            // Superadmin bypasses tenant scoping — they can see all tenants' data
            if (auth != null && hasRole(auth, "ROLE_SUPER_ADMIN")) {
                log.debug("Super admin detected. Bypassing tenant resolution.");
                TenantContext.setTenant(TenantContext.SUPER_ADMIN_TENANT);
                MDC.put(MDC_TENANT_KEY, TenantContext.SUPER_ADMIN_TENANT); // ← MDC
                chain.doFilter(request, response);
                return;
            }

            // Resolve tenant ID from JWT or header
            String tenantId = resolveTenantId(request, auth);

            if (tenantId == null || tenantId.isBlank()) {
                log.warn("Tenant ID could not be resolved for request to {}. Sending 400.", request.getRequestURI());
                sendError(response, "X-Tenant-ID header is required");
                return;
            }
            log.debug("Resolved tenant ID: {}", tenantId);

            // Set tenant for downstream services to use
            TenantContext.setTenant(tenantId);
            MDC.put(MDC_TENANT_KEY, tenantId); // ← MDC: overwrite the "resolving" placeholder

            chain.doFilter(request, response);

        } finally {
            TenantContext.clear(); // ALWAYS clean up — prevents thread pool leaks
        }
    }

    /**
     * Resolves tenant ID from multiple sources with priority:
     * 1. JWT claim (set by JwtAuthenticationFilter in auth details)
     * 2. X-Tenant-ID header
     */
    @SuppressWarnings("unchecked")
    private String resolveTenantId(HttpServletRequest request, Authentication auth) {

        // Priority 1: From JWT claim (already extracted in JwtAuthenticationFilter)
        if (auth != null && auth.getDetails() instanceof Map) {
            String tenantId = ((Map<String, String>) auth.getDetails()).get("tenantId");
            if (tenantId != null && !tenantId.isBlank()) {
                return tenantId;
            }
        }

        // Priority 2: From request header
        String header = request.getHeader("X-Tenant-ID");
        if (header != null && !header.isBlank()) {
            return header;
        }

        return null;
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }

    /** Sends a 400 Bad Request JSON error response and stops the filter chain. */
    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
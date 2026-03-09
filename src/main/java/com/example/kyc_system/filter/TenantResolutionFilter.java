package com.example.kyc_system.filter;

import com.example.kyc_system.context.TenantContext;
import com.example.kyc_system.entity.Tenant;
import com.example.kyc_system.repository.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Servlet filter that resolves and validates the tenant for every request.
 * Runs AFTER JwtAuthenticationFilter (Order 3), so authentication is already
 * available.
 *
 * Tenant resolution priority:
 * 1. JWT claim "tenantId" (from authenticated user's token)
 * 2. X-Tenant-ID request header (for API key or unauthenticated flows)
 * 3. X-API-Key header (looks up tenant by API key in the database)
 *
 * Super admins bypass tenant scoping entirely.
 * Always clears TenantContext in the finally block to prevent thread pool
 * leaks.
 */
@Component
@Order(3) // Runs after JwtAuthenticationFilter
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    // These paths don't need tenant context (public endpoints)
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/forgot-password",
            "/api/auth/change-password",
            "/swagger-ui/",
            "/v3/api-docs",
            "/swagger-ui");

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
                TenantContext.setTenant(TenantContext.SUPER_ADMIN_TENANT);
                chain.doFilter(request, response);
                return;
            }

            // Resolve tenant ID from JWT, header, or API key
            String tenantId = resolveTenantId(request, auth);

            if (tenantId == null || tenantId.isBlank()) {
                sendError(response, "X-Tenant-ID header is required");
                return;
            }

            // Validate tenant exists and is active in the database
            Tenant tenant = tenantRepository.findByTenantId(tenantId).orElse(null);

            if (tenant == null) {
                sendError(response, "Tenant not found: " + tenantId);
                return;
            }

            if (!tenant.getIsActive()) {
                sendError(response, "Tenant is inactive: " + tenantId);
                return;
            }

            // Set tenant for downstream services to use
            TenantContext.setTenant(tenantId);
            chain.doFilter(request, response);

        } finally {
            TenantContext.clear(); // ALWAYS clean up — prevents thread pool leaks
        }
    }

    /**
     * Resolves tenant ID from multiple sources with priority:
     * 1. JWT claim (set by JwtAuthenticationFilter in auth details)
     * 2. X-Tenant-ID header
     * 3. X-API-Key header (looks up tenant by API key)
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

        // Priority 2: From request header (for API key based access)
        String header = request.getHeader("X-Tenant-ID");
        if (header != null && !header.isBlank()) {
            return header;
        }

        // Priority 3: From API key header
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null) {
            return tenantRepository.findByApiKey(apiKey).map(Tenant::getTenantId).orElse(null);
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
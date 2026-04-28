package com.example.kyc_system.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that populates the SLF4J MDC (Mapped Diagnostic Context)
 * for every incoming HTTP request.
 *
 * Every log line emitted during a request automatically carries:
 * requestId — unique UUID per request (for log correlation)
 * userId — authenticated user's email (if logged in)
 *
 * TenantContext already sets tenantId in TenantResolutionFilter.
 * We also copy tenantId into MDC here so it appears in every log line
 * without each service needing to know about MDC.
 *
 * MDC is a ThreadLocal map, so:
 * - It is scoped to the current request thread
 * - It MUST be cleared in a finally block to prevent thread-pool leaks
 * - @Async methods won't inherit it (use InheritableThreadLocal or manual copy)
 *
 * Runs at Order(1) — before JwtAuthenticationFilter — so the requestId is
 * available from the very first log line of each request.
 */
@Component
@Order(1)
@Slf4j
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_KEY = "requestId";
    private static final String USER_ID_KEY = "userId";
    private static final String TENANT_ID_KEY = "tenantId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 1. Unique ID for this HTTP request — correlates all logs for one call
            String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put(REQUEST_ID_KEY, requestId);

            // 2. Add requestId to the response header so clients can correlate too
            response.setHeader("X-Request-ID", requestId);

            // 3. Authenticated user's email (may be null on public endpoints)
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                MDC.put(USER_ID_KEY, auth.getName());
            }

            // 4. Tenant from TenantContext (set later by TenantResolutionFilter,
            // but we add a placeholder so the key always exists in JSON)
            MDC.put(TENANT_ID_KEY, "resolving");

            log.debug("MDC context initialized for request: {}", request.getRequestURI());

            filterChain.doFilter(request, response);

        } finally {
            // CRITICAL: always clear MDC to prevent ThreadLocal leaks in thread pools
            MDC.remove(REQUEST_ID_KEY);
            MDC.remove(USER_ID_KEY);
            MDC.remove(TENANT_ID_KEY);
        }
    }
}
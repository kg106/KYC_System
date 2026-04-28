package com.example.kyc_system.security;

import com.example.kyc_system.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Authentication filter — runs on every request before Spring Security's
 * default auth.
 * Extracts the JWT from the Authorization header, validates it, checks if it's
 * blacklisted,
 * and sets the SecurityContext with the authenticated user details.
 *
 * Also extracts the tenantId claim from the JWT and stores it in the
 * Authentication's
 * details map, so TenantResolutionFilter can use it downstream.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
            UserDetailsService userDetailsService,
            TokenBlacklistService tokenBlacklistService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * Filters incoming requests to perform JWT authentication.
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param filterChain the filter chain
     * @throws ServletException in case of servlet errors
     * @throws IOException in case of I/O errors
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // Extract token from request header
        String token = getTokenFromRequest(request);

        // Only proceed if token exists, is valid, and hasn't been blacklisted (logged out)
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)
                && !tokenBlacklistService.isTokenBlacklisted(token)) {

            String username = jwtTokenProvider.getUsername(token);
            log.debug("JWT authenticated: user={}", username);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // Build the authentication object and set it in SecurityContext
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities());

            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Extract tenantId from JWT and attach it as additional metadata
            // This will be used by TenantResolutionFilter downstream
            String tenantId = jwtTokenProvider.getTenantIdFromToken(token);
            if (tenantId != null) {
                Map<String, String> extraDetails = new HashMap<>();
                extraDetails.put("tenantId", tenantId);
                authToken.setDetails(extraDetails);
            }

            // Set the authentication in the security context
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        // Continue the filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the Bearer token from the Authorization header of the request.
     *
     * @param request the HTTP request
     * @return the JWT token if present and validly formatted, otherwise null
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

package com.example.kyc_system.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * JWT Authentication filter — runs on every request before Spring Security's
 * default auth.
 * Extracts the JWT from the Authorization header, validates it using RS256,
 * and sets the SecurityContext with the authenticated user details from claims.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String token = getTokenFromRequest(request);

            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                
                String status = jwtTokenProvider.getStatus(token);
                if (!"ACTIVE".equals(status)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"User is " + status + "\"}");
                    return;
                }

                String username = jwtTokenProvider.getUsername(token);
                String userId = jwtTokenProvider.getUserId(token);
                String tenantId = jwtTokenProvider.getTenantId(token);
                List<String> roles = jwtTokenProvider.getRoles(token);

                List<SimpleGrantedAuthority> authorities = roles == null ? List.of() : 
                        roles.stream()
                             .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                             .map(SimpleGrantedAuthority::new)
                             .toList();

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        authorities);

                authToken.setDetails(Map.of(
                    "userId", userId,
                    "tenantId", tenantId,
                    "jwtToken", token
                ));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

package com.example.kyc_system.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.security.Key;
import java.util.Date;

/**
 * Utility class for JWT (JSON Web Token) operations:
 * - Token generation (with tenantId claim for multi-tenancy)
 * - Token validation
 * - Extracting username and tenantId from tokens
 * - Calculating remaining TTL (for blacklisting on logout)
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwt-secret}")
    private String jwtSecret;

    @Value("${app.jwt-expiration-milliseconds}")
    private long jwtExpirationDate;

    /**
     * Generates a JWT access token with all necessary claims for the frontend and tenant isolation.
     *
     * @param username the subject
     * @param tenantId the tenant scope
     * @param userId the internal user ID
     * @param roles the list of assigned roles (e.g. ROLE_USER)
     * @return a signed JWT token
     */
    public String generateToken(String username, String tenantId, Long userId, java.util.List<String> roles) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + jwtExpirationDate);

        return Jwts.builder()
                .setSubject(username)
                .claim("tenantId", tenantId)
                .claim("userId", userId)
                .claim("roles", roles)
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(key())
                .compact();
    }

    /**
     * Overload for Authentication object (login flow).
     */
    public String generateToken(Authentication authentication, String tenantId, Long userId) {
        java.util.List<String> roles = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toList());
        return generateToken(authentication.getName(), tenantId, userId, roles);
    }

    /**
     * Builds the HMAC signing key from the Base64-encoded secret.
     *
     * @return the cryptographic key for signing/verifying tokens
     */
    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    /**
     * Extracts the username (subject) from a JWT token.
     *
     * @param token the JWT token string
     * @return the username embedded in the token
     */
    public String getUsername(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody();
        return claims.getSubject();
    }

    /**
     * Extracts the tenantId custom claim from a JWT token.
     *
     * @param token the JWT token string
     * @return the tenantId claim value
     */
    public String getTenantIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody();
        return claims.get("tenantId", String.class);
    }

    /**
     * Validates the JWT token (checks signature, expiration, etc.).
     *
     * @param token the JWT token string
     * @return true if the token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parse(token);
            return true;
        } catch (io.jsonwebtoken.security.SignatureException | MalformedJwtException | ExpiredJwtException
                | UnsupportedJwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Returns the remaining time (ms) before this token expires.
     * Used by TokenBlacklistService to set Redis TTL matching the token's remaining life.
     *
     * @param token the JWT token string
     * @return remaining time in milliseconds
     */
    public long getExpirationRemaining(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody();
        return claims.getExpiration().getTime() - System.currentTimeMillis();
    }
}

package com.example.kyc_system.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

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
public class JwtTokenProvider {

    @Value("${app.jwt-secret}")
    private String jwtSecret;

    @Value("${app.jwt-expiration-milliseconds}")
    private long jwtExpirationDate;

    /**
     * Generates a JWT access token from the authenticated user with a tenantId
     * claim.
     * The tenantId is embedded so TenantResolutionFilter can scope requests without
     * a header.
     */
    public String generateToken(Authentication authentication, String tenantId) {
        String username = authentication.getName();
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + jwtExpirationDate);

        return Jwts.builder()
                .setSubject(username)
                .claim("tenantId", tenantId) // Embed tenant in token for multi-tenant scoping
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(key())
                .compact();
    }

    /** Generates a JWT token from username only (used during token refresh). */
    public String generateTokenFromUsername(String username) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + jwtExpirationDate);
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(key())
                .compact();
    }

    /** Builds the HMAC signing key from the Base64-encoded secret. */
    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    /** Extracts the username (subject) from a JWT token. */
    public String getUsername(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody();
        return claims.getSubject();
    }

    /** Extracts the tenantId custom claim from a JWT token. */
    public String getTenantIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody();
        return claims.get("tenantId", String.class);
    }

    /** Validates the JWT token (checks signature, expiration, etc.). */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parse(token);
            return true;
        } catch (io.jsonwebtoken.security.SignatureException | MalformedJwtException | ExpiredJwtException
                | UnsupportedJwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Returns the remaining time (ms) before this token expires.
     * Used by TokenBlacklistService to set Redis TTL matching the token's remaining
     * life.
     */
    public long getExpirationRemaining(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody();
        return claims.getExpiration().getTime() - System.currentTimeMillis();
    }
}

package com.example.kyc_system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Utility class for JWT (JSON Web Token) operations using Auth Service RS256 Keys:
 * - Token validation via JWKS
 * - Extracting custom claims (userId, tenantId, status, roles)
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final JwksKeyProvider keyProvider;

    public JwtTokenProvider(JwksKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public Claims validateAndGetClaims(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(keyProvider.getPublicKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        log.info("JWT Claims from Auth Service: {}", claims);
        return claims;
    }

    public String getUsername(String token) {
        return validateAndGetClaims(token).getSubject();
    }

    public String getUserId(String token) {
        return validateAndGetClaims(token).get("userId", String.class);
    }

    public String getTenantId(String token) {
        return validateAndGetClaims(token).get("tenantId", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return validateAndGetClaims(token).get("roles", List.class);
    }

    public String getStatus(String token) {
        return validateAndGetClaims(token).get("status", String.class);
    }

    public boolean validateToken(String token) {
        try {
            validateAndGetClaims(token);
            return true;
        } catch (io.jsonwebtoken.security.SignatureException | MalformedJwtException | ExpiredJwtException
                | UnsupportedJwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }
}


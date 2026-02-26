package com.example.kyc_system.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${app.jwt-secret}")
    private String jwtSecret;

    @Value("${app.jwt-expiration-milliseconds}")
    private long jwtExpirationDate;

    // generate JWT token from Authentication
    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        return generateTokenFromUsername(username);
    }

    // generate JWT token from username directly (used for refresh)
    public String generateTokenFromUsername(String username) {
        Date currentDate = new Date();
        Date expireDate = new Date(currentDate.getTime() + jwtExpirationDate);

        String token = Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(expireDate)
                .signWith(key())
                .compact();
        return token;
    }

    private Key key() {
        return Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(jwtSecret));
    }

    // get username from Jwt token
    public String getUsername(String token) {
        Claims claims = getClaims(token);
        return claims.getSubject();
    }

    // get remaining expiration time in milliseconds
    public long getExpirationRemaining(String token) {
        try {
            Claims claims = getClaims(token);
            Date expiration = claims.getExpiration();
            long remaining = expiration.getTime() - System.currentTimeMillis();
            return remaining > 0 ? remaining : 0;
        } catch (Exception e) {
            return 0; // If token is invalid or already expired
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // validate Jwt token
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key())
                    .build()
                    .parse(token);
            return true;
        } catch (MalformedJwtException e) {
            throw new RuntimeException("Invalid JWT token");
        } catch (ExpiredJwtException e) {
            throw new RuntimeException("Expired JWT token");
        } catch (UnsupportedJwtException e) {
            throw new RuntimeException("Unsupported JWT token");
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("JWT claims string is empty.");
        }
    }
}

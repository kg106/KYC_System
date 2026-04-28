package com.example.kyc_system.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    @Value("${app.jwt-refresh-expiration-milliseconds}")
    private long refreshExpiration;

    public Cookie createRefreshTokenCookie(String refreshToken) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // Should be true in production (HTTPS)
        cookie.setPath("/api/auth"); // Restrict to auth endpoints
        cookie.setMaxAge((int) (refreshExpiration / 1000));
        // cookie.setAttribute("SameSite", "Strict"); // Required higher servlet API or
        // manual header
        return cookie;
    }

    public Cookie createEmptyCookie() {
        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0); // Delete cookie
        return cookie;
    }

    public String getRefreshTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}

package com.example.kyc_system.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("CookieUtil Unit Tests")
class CookieUtilTest {

    private CookieUtil cookieUtil;
    private static final long REFRESH_EXPIRATION = 86400000; // 24 hours

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cookieUtil = new CookieUtil();
        ReflectionTestUtils.setField(cookieUtil, "refreshExpiration", REFRESH_EXPIRATION);
    }

    @Test
    @DisplayName("Should create a valid refresh token cookie")
    void createRefreshTokenCookie_ValidInput_ReturnsCorrectCookie() {
        String token = "test-refresh-token";
        Cookie cookie = cookieUtil.createRefreshTokenCookie(token);

        assertNotNull(cookie);
        assertEquals("refreshToken", cookie.getName());
        assertEquals(token, cookie.getValue());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.getSecure());
        assertEquals("/api/auth", cookie.getPath());
        assertEquals((int) (REFRESH_EXPIRATION / 1000), cookie.getMaxAge());
    }

    @Test
    @DisplayName("Should create an empty (deletion) cookie")
    void createEmptyCookie_ReturnsDeletionCookie() {
        Cookie cookie = cookieUtil.createEmptyCookie();

        assertNotNull(cookie);
        assertEquals("refreshToken", cookie.getName());
        assertNull(cookie.getValue());
        assertEquals(0, cookie.getMaxAge());
    }

    @Test
    @DisplayName("Should extract refresh token from cookies when present")
    void getRefreshTokenFromCookies_Present_ReturnsToken() {
        Cookie[] cookies = {
                new Cookie("someOther", "value"),
                new Cookie("refreshToken", "extracted-token")
        };
        when(request.getCookies()).thenReturn(cookies);

        String result = cookieUtil.getRefreshTokenFromCookies(request);
        assertEquals("extracted-token", result);
    }

    @Test
    @DisplayName("Should return null when refresh token cookie is missing")
    void getRefreshTokenFromCookies_Missing_ReturnsNull() {
        Cookie[] cookies = { new Cookie("someOther", "value") };
        when(request.getCookies()).thenReturn(cookies);

        String result = cookieUtil.getRefreshTokenFromCookies(request);
        assertNull(result);
    }

    @Test
    @DisplayName("Should return null when no cookies are present in request")
    void getRefreshTokenFromCookies_NoCookies_ReturnsNull() {
        when(request.getCookies()).thenReturn(null);

        String result = cookieUtil.getRefreshTokenFromCookies(request);
        assertNull(result);
    }
}

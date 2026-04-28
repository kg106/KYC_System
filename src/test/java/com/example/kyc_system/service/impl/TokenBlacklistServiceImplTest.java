package com.example.kyc_system.service.impl;

import com.example.kyc_system.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TokenBlacklistServiceImpl Unit Tests")
class TokenBlacklistServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private TokenBlacklistServiceImpl tokenBlacklistService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("Should blacklist token with correct TTL")
    void blacklistToken_ValidToken_SetsInRedis() {
        String token = "valid-token";
        when(jwtTokenProvider.getExpirationRemaining(token)).thenReturn(5000L);

        tokenBlacklistService.blacklistToken(token);

        verify(valueOps).set(eq("BLACKLIST:valid-token"), eq("revoked"), eq(Duration.ofMillis(5000)));
    }

    @Test
    @DisplayName("Should not blacklist token if expired")
    void blacklistToken_ExpiredToken_DoesNothing() {
        String token = "expired-token";
        when(jwtTokenProvider.getExpirationRemaining(token)).thenReturn(-100L);

        tokenBlacklistService.blacklistToken(token);

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Should return true if token is in blacklist")
    void isTokenBlacklisted_Present_ReturnsTrue() {
        String token = "blacklisted-token";
        when(redisTemplate.hasKey("BLACKLIST:" + token)).thenReturn(true);

        assertTrue(tokenBlacklistService.isTokenBlacklisted(token));
    }

    @Test
    @DisplayName("Should return false if token is not in blacklist")
    void isTokenBlacklisted_Absent_ReturnsFalse() {
        String token = "clean-token";
        when(redisTemplate.hasKey("BLACKLIST:" + token)).thenReturn(false);

        assertFalse(tokenBlacklistService.isTokenBlacklisted(token));
    }
}

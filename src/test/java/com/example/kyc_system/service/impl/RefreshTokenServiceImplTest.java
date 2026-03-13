package com.example.kyc_system.service.impl;

import com.example.kyc_system.dto.JwtAuthResponse;
import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.repository.UserRepository;
import com.example.kyc_system.security.JwtTokenProvider;
import com.example.kyc_system.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RefreshTokenServiceImpl Unit Tests")
class RefreshTokenServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;
    @Mock
    private UserService userService;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationMs", 86400000L);
    }

    @Test
    @DisplayName("Should create refresh token successfully")
    void createRefreshToken_Success_ReturnsToken() {
        UserDTO user = UserDTO.builder().id(1L).email("user@test.com").build();
        when(userService.getUserById(1L)).thenReturn(user);

        String result = refreshTokenService.createRefreshToken(1L);

        assertNotNull(result);
        assertTrue(result.contains(":"));
        verify(valueOps).set(anyString(), anyString(), any(Duration.class));
        verify(setOps).add(anyString(), anyString());
    }

    @Test
    @DisplayName("Should process valid refresh token and rotate")
    void processRefreshToken_Valid_ReturnsNewTokens() {
        String rawToken = "family:oldValue";
        String storedValue = "user@test.com:oldValue";
        when(valueOps.get("RT_FAMILY:family")).thenReturn(storedValue);
        when(jwtTokenProvider.generateTokenFromUsername("user@test.com")).thenReturn("new-access-token");

        JwtAuthResponse response = refreshTokenService.processRefreshToken(rawToken);

        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertNotEquals(rawToken, response.getRefreshToken());
        verify(valueOps).set(eq("RT_FAMILY:family"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Should detect reuse and revoke family")
    void processRefreshToken_Reuse_RevokesFamily() {
        String rawToken = "family:stolenToken";
        String storedValue = "user@test.com:newValue"; // Already rotated
        when(valueOps.get("RT_FAMILY:family")).thenReturn(storedValue);

        assertThrows(RuntimeException.class, () -> refreshTokenService.processRefreshToken(rawToken));
        verify(redisTemplate).delete("RT_FAMILY:family");
    }

    @Test
    @DisplayName("Should revoke all tokens for user")
    void revokeAllForUser_Success() {
        UserDTO user = UserDTO.builder().id(1L).email("user@test.com").build();
        when(userService.getUserById(1L)).thenReturn(user);
        when(setOps.members("USER_FAMILIES:user@test.com")).thenReturn(Set.of("f1", "f2"));

        refreshTokenService.revokeAllForUser(1L);

        verify(redisTemplate).delete("RT_FAMILY:f1");
        verify(redisTemplate).delete("RT_FAMILY:f2");
        verify(redisTemplate).delete("USER_FAMILIES:user@test.com");
    }
}

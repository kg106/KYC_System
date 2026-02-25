package com.example.kyc_system.service.impl;

import com.example.kyc_system.dto.JwtAuthResponse;
import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.security.JwtTokenProvider;
import com.example.kyc_system.service.RefreshTokenService;
import com.example.kyc_system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final StringRedisTemplate redisTemplate;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.jwt-refresh-expiration-milliseconds}")
    private long refreshExpirationMs;

    private static final String RT_FAMILY_PREFIX = "RT_FAMILY:";
    private static final String USER_FAMILIES_PREFIX = "USER_FAMILIES:";

    @Override
    public String createRefreshToken(Long userId) {
        UserDTO user = userService.getUserById(userId);
        String email = user.getEmail();

        String familyId = UUID.randomUUID().toString();
        String tokenValue = UUID.randomUUID().toString();

        String redisKey = RT_FAMILY_PREFIX + familyId;
        String redisValue = email + ":" + tokenValue;

        // Save token to Valkey/Redis
        redisTemplate.opsForValue().set(redisKey, redisValue, Duration.ofMillis(refreshExpirationMs));

        // Add family to user's list of families
        String userFamiliesKey = USER_FAMILIES_PREFIX + email;
        redisTemplate.opsForSet().add(userFamiliesKey, familyId);

        return familyId + ":" + tokenValue;
    }

    @Override
    public JwtAuthResponse processRefreshToken(String rawToken) {
        if (rawToken == null || !rawToken.contains(":")) {
            throw new RuntimeException("Invalid refresh token format");
        }

        String[] parts = rawToken.split(":");
        if (parts.length != 2) {
            throw new RuntimeException("Invalid refresh token format");
        }

        String familyId = parts[0];
        String presentedTokenValue = parts[1];

        String redisKey = RT_FAMILY_PREFIX + familyId;
        String storedValue = redisTemplate.opsForValue().get(redisKey);

        if (storedValue == null) {
            throw new RuntimeException("Refresh token expired or revoked");
        }

        String[] storedParts = storedValue.split(":", 2);
        String email = storedParts[0];
        String currentTokenValue = storedParts[1];

        if (!presentedTokenValue.equals(currentTokenValue)) {
            // REUSE DETECTED! Potential token theft. Revoke entire family.
            revokeFamily(familyId);
            throw new RuntimeException("Refresh token reuse detected. Family revoked.");
        }

        // Token is valid. Rotate it.
        String newTokenValue = UUID.randomUUID().toString();
        String newRedisValue = email + ":" + newTokenValue;

        // Reset expiration on rotation (optional, but standard to keep session alive)
        // Alternatively, keep the original TTL. We'll set a new TTL for simplicity.
        redisTemplate.opsForValue().set(redisKey, newRedisValue, Duration.ofMillis(refreshExpirationMs));

        // Generate new Access Token
        String newAccessToken = jwtTokenProvider.generateTokenFromUsername(email);

        String newRawRefreshToken = familyId + ":" + newTokenValue;

        JwtAuthResponse response = new JwtAuthResponse();
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(newRawRefreshToken);

        return response;
    }

    @Override
    public void revokeFamily(String familyId) {
        String redisKey = RT_FAMILY_PREFIX + familyId;
        String storedValue = redisTemplate.opsForValue().get(redisKey);

        if (storedValue != null) {
            String email = storedValue.split(":", 2)[0];
            String userFamiliesKey = USER_FAMILIES_PREFIX + email;
            redisTemplate.opsForSet().remove(userFamiliesKey, familyId);
            redisTemplate.delete(redisKey);
        }
    }

    @Override
    public void revokeAllForUser(Long userId) {
        UserDTO user = userService.getUserById(userId);
        String email = user.getEmail();
        String userFamiliesKey = USER_FAMILIES_PREFIX + email;

        java.util.Set<String> families = redisTemplate.opsForSet().members(userFamiliesKey);
        if (families != null && !families.isEmpty()) {
            for (String familyId : families) {
                redisTemplate.delete(RT_FAMILY_PREFIX + familyId);
            }
            redisTemplate.delete(userFamiliesKey);
        }
    }
}

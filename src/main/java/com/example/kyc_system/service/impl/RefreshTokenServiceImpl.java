package com.example.kyc_system.service.impl;

import com.example.kyc_system.dto.JwtAuthResponse;
import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.security.JwtTokenProvider;
import com.example.kyc_system.service.RefreshTokenService;
import com.example.kyc_system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

import com.example.kyc_system.repository.*;
import java.util.*;

/**
 * Implementation of RefreshTokenService.
 * Manages "Refresh Token Families" in Redis/Valkey for secure session management.
 * Features:
 * - Token Family Rotation: Issuing a new refresh token with every use.
 * - Reuse Detection: Revoking an entire family if an old token is presented.
 * - Multi-device support: Tracking multiple families per user email.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final StringRedisTemplate redisTemplate;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Value("${app.jwt-refresh-expiration-milliseconds}")
    private long refreshExpirationMs;

    /** Prefix for individual token family keys in Redis. */
    private static final String RT_FAMILY_PREFIX = "RT_FAMILY:";
    /** Prefix for the set of family IDs belonging to a user. */
    private static final String USER_FAMILIES_PREFIX = "USER_FAMILIES:";

    /**
     * Initializes a new refresh token family for a user.
     * Uses TenantContext-scoped UserService for info.
     *
     * @param userId user ID
     * @return raw token string (familyId:tokenValue)
     */
    @Override
    public String createRefreshToken(Long userId) {
        UserDTO user = userService.getUserById(userId);
        String email = user.getEmail();

        String familyId = UUID.randomUUID().toString();
        String tokenValue = UUID.randomUUID().toString();

        log.info("Creating refresh token family: userId={}, email={}, familyId={}", userId, email, familyId);
        String redisKey = RT_FAMILY_PREFIX + familyId;
        String redisValue = email + ":" + tokenValue;

        // Save token to Valkey/Redis
        redisTemplate.opsForValue().set(redisKey, redisValue, Duration.ofMillis(refreshExpirationMs));

        // Add family to user's list of families
        String userFamiliesKey = USER_FAMILIES_PREFIX + email;
        redisTemplate.opsForSet().add(userFamiliesKey, familyId);

        return familyId + ":" + tokenValue;
    }

    /**
     * Creates a refresh token family directly from UserRepository.
     * Used during initial login where TenantContext may not be initialized yet.
     *
     * @param userId user ID
     * @return raw token string
     */
    @Override
    public String createRefreshTokenDirect(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Failed to create refresh token: User {} not found", userId);
                    return new RuntimeException("User not found");
                });
        String email = user.getEmail();

        String familyId = UUID.randomUUID().toString();
        String tokenValue = UUID.randomUUID().toString();

        log.info("Creating refresh token family (direct): userId={}, email={}, familyId={}", userId, email, familyId);
        String redisKey = RT_FAMILY_PREFIX + familyId;
        String redisValue = email + ":" + tokenValue;

        redisTemplate.opsForValue().set(redisKey, redisValue, Duration.ofMillis(refreshExpirationMs));

        String userFamiliesKey = USER_FAMILIES_PREFIX + email;
        redisTemplate.opsForSet().add(userFamiliesKey, familyId);

        return familyId + ":" + tokenValue;
    }

    /**
     * Processes a refresh request by presenting the current refresh token.
     * Detects reuse and rotates the token upon success.
     *
     * @param rawToken the refresh token presented by the client
     * @return new access and refresh tokens
     */
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
            log.warn("REFRESH TOKEN REUSE DETECTED: email={}, familyId={}. Revoking all tokens in family.", email,
                    familyId);
            revokeFamily(familyId);
            throw new RuntimeException("Refresh token reuse detected. Family revoked.");
        }

        // Token is valid. Rotate it.
        log.info("Rotating refresh token for family: email={}, familyId={}", email, familyId);
        String newTokenValue = UUID.randomUUID().toString();
        String newRedisValue = email + ":" + newTokenValue;

        // Reset expiration on rotation (standard behavior to extend session)
        redisTemplate.opsForValue().set(redisKey, newRedisValue, Duration.ofMillis(refreshExpirationMs));

        // Generate new Access Token
        String newAccessToken = jwtTokenProvider.generateTokenFromUsername(email);

        String newRawRefreshToken = familyId + ":" + newTokenValue;

        JwtAuthResponse response = new JwtAuthResponse();
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(newRawRefreshToken);

        return response;
    }

    /**
     * Revokes a specific refresh token family, logging out that specific session.
     *
     * @param familyId individual family ID
     */
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

    /**
     * Revokes all refresh token families for a user, effectively logging them out everywhere.
     * Used after password resets or administrative lockouts.
     *
     * @param userId user ID
     */
    @Override
    public void revokeAllForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String email = user.getEmail();
        String userFamiliesKey = USER_FAMILIES_PREFIX + email;

        Set<String> families = redisTemplate.opsForSet().members(userFamiliesKey);
        if (families != null && !families.isEmpty()) {
            for (String familyId : families) {
                redisTemplate.delete(RT_FAMILY_PREFIX + familyId);
            }
            redisTemplate.delete(userFamiliesKey);
        }
    }
}

package com.example.kyc_system.service.impl;

import com.example.kyc_system.security.JwtTokenProvider;
import com.example.kyc_system.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Implementation of TokenBlacklistService.
 * Uses Redis/Valkey to store revoked JWTs until their original expiration time.
 * This provides a stateless way to handle logouts and token invalidation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    /** Key prefix for blacklisted tokens in Redis. */
    private static final String BLACKLIST_PREFIX = "BLACKLIST:";

    /**
     * Blacklists a token by storing it in Redis with a TTL equal to its 
     * remaining validity period.
     *
     * @param token the JWT to revoke
     */
    @Override
    public void blacklistToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }

        try {
            long expirationRemainingMs = jwtTokenProvider.getExpirationRemaining(token);
            if (expirationRemainingMs > 0) {
                String redisKey = BLACKLIST_PREFIX + token;
                log.info("Blacklisting token for {} ms", expirationRemainingMs);
                // Store the token with a TTL corresponding to its remaining validity
                redisTemplate.opsForValue().set(redisKey, "revoked", Duration.ofMillis(expirationRemainingMs));
            }
        } catch (Exception e) {
            // Token might already be expired or invalid
            log.trace("Suppressed error blacklisting token: {}", e.getMessage());
        }
    }

    /**
     * Checks if a token is currently present in the blacklist.
     *
     * @param token the JWT to check
     * @return true if revoked, false otherwise
     */
    @Override
    public boolean isTokenBlacklisted(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        String redisKey = BLACKLIST_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }
}

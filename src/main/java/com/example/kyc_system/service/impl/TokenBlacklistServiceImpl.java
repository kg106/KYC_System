package com.example.kyc_system.service.impl;

import com.example.kyc_system.security.JwtTokenProvider;
import com.example.kyc_system.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    private static final String BLACKLIST_PREFIX = "BLACKLIST:";

    @Override
    public void blacklistToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }

        try {
            long expirationRemainingMs = jwtTokenProvider.getExpirationRemaining(token);
            if (expirationRemainingMs > 0) {
                String redisKey = BLACKLIST_PREFIX + token;
                // Store the token in Valkey/Redis with a TTL corresponding to its remaining
                // validity
                redisTemplate.opsForValue().set(redisKey, "revoked", Duration.ofMillis(expirationRemainingMs));
            }
        } catch (Exception e) {
            // Token might already be expired or invalid; we can optionally log this, but no
            // action needed
        }
    }

    @Override
    public boolean isTokenBlacklisted(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        String redisKey = BLACKLIST_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }
}

package com.example.kyc_system.service;

/**
 * Service interface for JWT access token blacklisting.
 * Tokens are stored in Redis/Valkey with TTL matching the token's remaining
 * validity.
 * Used during logout to prevent already-issued tokens from being used.
 */
public interface TokenBlacklistService {
    void blacklistToken(String token);

    boolean isTokenBlacklisted(String token);
}

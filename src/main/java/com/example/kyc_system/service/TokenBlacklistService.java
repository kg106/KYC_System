package com.example.kyc_system.service;

public interface TokenBlacklistService {
    void blacklistToken(String token);

    boolean isTokenBlacklisted(String token);
}

package com.example.kyc_system.service;

import com.example.kyc_system.dto.JwtAuthResponse;

public interface RefreshTokenService {
    String createRefreshToken(Long userId);

    JwtAuthResponse processRefreshToken(String rawToken);

    void revokeFamily(String familyId);

    void revokeAllForUser(Long userId);
}

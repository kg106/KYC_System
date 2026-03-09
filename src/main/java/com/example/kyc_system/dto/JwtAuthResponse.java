package com.example.kyc_system.dto;

import lombok.*;

/**
 * DTO returned after successful login.
 * Contains the JWT access token and the refresh token.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JwtAuthResponse {
    private String accessToken; // Short-lived JWT (default: 15 min)
    private String tokenType = "Bearer";
    private String refreshToken; // Long-lived refresh token (default: 7 days)
}

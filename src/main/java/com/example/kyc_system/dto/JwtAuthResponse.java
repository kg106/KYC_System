package com.example.kyc_system.dto;

import lombok.*;

/**
 * Response DTO containing authentication tokens.
 * Returned after successful login or token refresh.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JwtAuthResponse {
    /** The short-lived JWT access token. */
    private String accessToken;

    /** The type of token (always "Bearer"). */
    private String tokenType = "Bearer";

    /** The long-lived refresh token used to obtain new access tokens. */
    private String refreshToken;
}

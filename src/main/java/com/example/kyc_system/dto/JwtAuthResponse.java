package com.example.kyc_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JwtAuthResponse {
    @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBreWMuY29tIiwiaWF0IjoxNzM5NjkwNTI3LCJleHAiOjE3Mzk3NzY5Mjd9...", description = "JWT Access Token")
    private String accessToken;
    @Schema(example = "Bearer")
    private String tokenType = "Bearer";
}

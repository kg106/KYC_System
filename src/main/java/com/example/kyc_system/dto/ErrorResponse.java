package com.example.kyc_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {
    @Schema(example = "2026-02-16T17:27:55.309")
    private LocalDateTime timestamp;
    @Schema(example = "400")
    private int status;
    @Schema(example = "Bad Request")
    private String error;
    @Schema(example = "Validation failed for field 'email'")
    private String message;
    @Schema(example = "/api/auth/register")
    private String path;
}

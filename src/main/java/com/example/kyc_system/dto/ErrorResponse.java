package com.example.kyc_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Standard error response DTO for API exceptions.
 * Follows Spring Boot's default error attributes structure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {
    /** Time when the error occurred. */
    @Schema(example = "2026-02-16T17:27:55.309")
    private LocalDateTime timestamp;

    /** HTTP status code (e.g., 400, 404, 500). */
    @Schema(example = "400")
    private int status;

    /** Short error description (e.g., "Bad Request"). */
    @Schema(example = "Bad Request")
    private String error;

    /** Detailed error message. */
    @Schema(example = "Validation failed for field 'email'")
    private String message;

    /** The URI path where the error occurred. */
    @Schema(example = "/api/auth/register")
    private String path;
}

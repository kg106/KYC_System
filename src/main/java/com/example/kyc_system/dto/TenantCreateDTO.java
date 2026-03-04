package com.example.kyc_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantCreateDTO {

    @Schema(example = "hdfc_bank")
    @NotBlank(message = "Tenant ID is required")
    @Pattern(regexp = "^[a-z0-9_]{3,50}$", message = "Tenant ID must be lowercase, alphanumeric with underscores only")
    private String tenantId;

    @Schema(example = "HDFC Bank Limited")
    @NotBlank(message = "Name is required")
    private String name;

    @Schema(example = "kyc-ops@hdfcbank.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email")
    private String email;

    @Schema(example = "5", description = "Max KYC attempts per user per day")
    private Integer maxDailyAttempts;

    @Schema(example = "[\"PAN\", \"AADHAAR\"]")
    private List<String> allowedDocumentTypes;

    // Auto-provision a TENANT_ADMIN user for this tenant
    @Schema(example = "admin@hdfcbank.com")
    @Email(message = "Please provide a valid admin email")
    private String adminEmail;

    @Schema(example = "Admin@123")
    private String adminPassword;
}
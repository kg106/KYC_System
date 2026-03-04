package com.example.kyc_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUpdateDTO {

    @Schema(example = "HDFC Bank Updated Name")
    private String name;

    @Schema(example = "new-ops@hdfcbank.com")
    @Email(message = "Please provide a valid email")
    private String email;

    @Schema(example = "10")
    @Min(value = 1, message = "Max daily attempts must be at least 1")
    private Integer maxDailyAttempts;

    @Schema(example = "[\"PAN\", \"AADHAAR\", \"PASSPORT\"]")
    private List<String> allowedDocumentTypes;
}
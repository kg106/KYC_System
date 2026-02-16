package com.example.kyc_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequestDTO {
    @Schema(example = "user@example.com", description = "Registered email address to receive reset token")
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;
}

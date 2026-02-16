package com.example.kyc_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetDTO {
    @Schema(example = "user@example.com", description = "Registered email address")
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @Schema(example = "A1B2C3", description = "6-character reset token received via email")
    @NotBlank(message = "Token is required")
    private String token;

    @Schema(example = "NewPassword@123", description = "New user password")
    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[@#$%^&+=!_]).{8,}$", message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character")
    private String newPassword;
}

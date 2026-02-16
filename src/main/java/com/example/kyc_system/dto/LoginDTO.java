package com.example.kyc_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginDTO {
    @Schema(example = "admin@kyc.com", description = "Registered email address")
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$", message = "Please provide a valid email address with a proper domain (e.g., .com, .org)")
    private String email;

    @Schema(example = "Admin@123", description = "User password")
    @NotBlank(message = "Password is required")
    private String password;
}

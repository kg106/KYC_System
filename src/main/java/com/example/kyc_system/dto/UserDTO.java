package com.example.kyc_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    @Schema(example = "1", description = "Unique identifier of the user")
    private Long id;

    @Schema(example = "John Doe", description = "Full name of the user")
    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Name can only contain alphabets and spaces")
    private String name;

    @Schema(example = "john.doe@example.com", description = "Email address of the user")
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$", message = "Please provide a valid email address with a proper domain (e.g., .com, .org)")
    private String email;

    @Schema(example = "9876543210", description = "10-digit mobile number")
    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String mobileNumber;

    @Schema(example = "Welcome@123", description = "User password (must meet complexity requirements)")
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[@#$%^&+=!_]).{8,}$", message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character")
    private String password;

    @Schema(example = "true")
    private Boolean isActive;

    @Schema(example = "1990-01-01")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dob;
}

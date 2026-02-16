package com.example.kyc_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateDTO {

    @Schema(example = "John Doe", description = "Full name of the user")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Name can only contain alphabets and spaces")
    private String name;

    @Schema(example = "john.doe@example.com", description = "Email address of the user")
    @Email(message = "Please provide a valid email address")
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$", message = "Please provide a valid email address with a proper domain (e.g., .com, .org)")
    private String email;

    @Schema(example = "9876543210", description = "10-digit mobile number")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String mobileNumber;

    @Schema(example = "true")
    private Boolean isActive;

    @Schema(example = "1990-01-01")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dob;
}

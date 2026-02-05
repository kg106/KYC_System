package com.example.kyc_system.dto;

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

    private Long id;

    private String name;

    private String email;

    private String mobileNumber;

    private String password; // Write-only for creation/updates if needed, though update usually excludes it

    private Boolean isActive;

    private LocalDate dob;
}

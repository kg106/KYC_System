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
public class AuthUserDTO {
    private String id;
    private String name;
    private String email;
    private String mobileNumber;
    private String status;
    private String tenantId;
    private LocalDate dob;
}

package com.example.kyc_system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchDTO {
    private String name;
    private String email;
    private String mobileNumber;
    private Boolean isActive;
}

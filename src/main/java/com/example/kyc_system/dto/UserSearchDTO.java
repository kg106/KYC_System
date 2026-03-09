package com.example.kyc_system.dto;

import lombok.*;

/**
 * DTO for searching/filtering users.
 * All fields are optional — only non-null fields are used as filter criteria.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSearchDTO {
    private String name;
    private String email;
    private String mobileNumber;
    private Boolean isActive;
}

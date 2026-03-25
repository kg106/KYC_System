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
    /** Filter by user's full name. */
    private String name;

    /** Filter by user's email. */
    private String email;

    /** Filter by user's mobile number. */
    private String mobileNumber;

    /** Filter by account active status. */
    private Boolean isActive;
}

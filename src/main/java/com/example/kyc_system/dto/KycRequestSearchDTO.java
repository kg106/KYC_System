package com.example.kyc_system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Search/Filter criteria for KYC requests.
 * Fields are optional.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycRequestSearchDTO {
    /** Filter by user ID. */
    private Long userId;

    /** Filter by user's full name (partial match). */
    private String userName;

    /** Filter by request status (e.g., "VERIFIED", "FAILED"). */
    private String status;

    /** Filter by document type (e.g., "PAN"). */
    private String documentType;

    /** Filter by submission date (start). */
    private LocalDateTime dateFrom;

    /** Filter by submission date (end). */
    private LocalDateTime dateTo;
}

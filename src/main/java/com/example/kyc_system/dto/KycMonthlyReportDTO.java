package com.example.kyc_system.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// dto/KycMonthlyReportDTO.java
/**
 * Aggregate report DTO for KYC performance and user registration metrics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycMonthlyReportDTO {
    /** Start date of the reporting period. */
    private LocalDate dateFrom;

    /** End date of the reporting period. */
    private LocalDate dateTo;

    /** Total number of KYC requests in this period. */
    private long totalRequests;
    private long verified;
    private long failed;
    private long pending; // PENDING + SUBMITTED + PROCESSING
    private double passRate;
    private Map<String, Long> breakdownByDocumentType; // PAN=12, AADHAAR=8, etc.
    private long newUsersRegistered;
    private long totalActiveUsers;
    private List<KycReportDataDTO> kycData;
}
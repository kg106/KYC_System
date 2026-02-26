package com.example.kyc_system.dto;

import java.time.YearMonth;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

// dto/KycMonthlyReportDTO.java
@Data
@Builder
public class KycMonthlyReportDTO {
    private YearMonth reportMonth;
    private long totalRequests;
    private long verified;
    private long failed;
    private long pending; // PENDING + SUBMITTED + PROCESSING
    private double passRate;
    private Map<String, Long> breakdownByDocumentType; // PAN=12, AADHAAR=8, etc.
    private long newUsersRegistered;
    private long totalActiveUsers;
}
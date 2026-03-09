package com.example.kyc_system.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KycReportDataDTO {
    private Long userId;
    private Long kycRequestId;
    private String userName;
    private LocalDate dob;
    private String status;
    private String mobileNumber;
    private String email;
    private String tenantId;
    private String tenantName;
    private String documentType;
    private Integer attemptNumber;
    private BigDecimal nameMatchScore;
    private String decisionReason;
}

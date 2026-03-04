package com.example.kyc_system.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantStatsDTO {
    private String tenantId;
    private String tenantName;
    private long totalUsers;
    private long totalKycRequests;
    private long verified;
    private long failed;
    private long pending;
    private double passRate;
}
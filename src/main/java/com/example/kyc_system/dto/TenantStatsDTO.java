package com.example.kyc_system.dto;

import lombok.*;

/**
 * Data Transfer Object for tenant-level KYC statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantStatsDTO {
    /** Logical identifier of the tenant. */
    private String tenantId;

    /** Human-readable name of the tenant. */
    private String tenantName;

    /** Total number of users registered under this tenant. */
    private long totalUsers;

    /** Total number of KYC requests submitted. */
    private long totalKycRequests;
    private long verified;
    private long failed;
    private long pending;
    private double passRate;
}
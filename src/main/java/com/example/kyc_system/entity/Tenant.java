package com.example.kyc_system.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing a tenant in the multi-tenant KYC system.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {

    /**
     * Unique identifier for the tenant record.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique business identifier for the tenant (e.g., "T001").
     */
    @Column(name = "tenant_id", nullable = false, unique = true, length = 50)
    private String tenantId;

    /**
     * The name of the tenant organization.
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Contact email for the tenant.
     */
    @Column(nullable = false, length = 100)
    private String email;

    /**
     * Subscription plan for the tenant (e.g., "BASIC", "PREMIUM").
     */
    @Builder.Default
    @Column(length = 20)
    private String plan = "BASIC";

    /**
     * Whether the tenant account is active.
     */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Maximum number of KYC attempts allowed per user per day for this tenant.
     */
    @Builder.Default
    @Column(name = "max_daily_attempts", nullable = false)
    private Integer maxDailyAttempts = 5;

    /**
     * Comma-separated list of allowed document types (e.g., "PAN,AADHAAR").
     */
    @Builder.Default
    @Column(name = "allowed_document_types", length = 200)
    private String allowedDocumentTypes = "PAN,AADHAAR";

    /**
     * Unique API key for the tenant to access the system programmatically.
     */
    @Column(name = "api_key", unique = true)
    private String apiKey;
}
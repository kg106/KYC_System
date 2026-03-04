package com.example.kyc_system.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true, length = 50)
    private String tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String email;

    // Stored for future plan-based features, no logic built yet
    @Builder.Default
    @Column(length = 20)
    private String plan = "BASIC";

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "max_daily_attempts", nullable = false)
    private Integer maxDailyAttempts = 5;

    // Comma separated: "PAN,AADHAAR"
    @Builder.Default
    @Column(name = "allowed_document_types", length = 200)
    private String allowedDocumentTypes = "PAN,AADHAAR";

    @Column(name = "api_key", unique = true)
    private String apiKey;
}
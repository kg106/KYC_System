package com.example.kyc_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Entity representing a KYC verification request.
 */
@Entity
@Table(name = "kyc_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycRequest extends BaseEntity {

    /**
     * Unique identifier for the KYC request.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The UUID of the user who submitted the KYC request.
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.UUID)
    @Column(name = "user_id", nullable = false)
    private java.util.UUID userId;

    /**
     * ID of the tenant the request belongs to.
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.UUID)
    @Column(name = "tenant_id", nullable = false)
    private java.util.UUID tenantId;

    /**
     * The current status of the KYC request (e.g., "SUBMITTED", "PROCESSING", "VERIFIED", "REJECTED").
     */
    @Column(length = 20, nullable = false)
    private String status;

    /**
     * The type of document submitted for verification.
     */
    @Column(name = "document_type", length = 20, nullable = false)
    private String documentType;

    /**
     * The number of times this request has been attempted.
     */
    @Builder.Default
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    /**
     * The reason for failure, if the request was rejected or failed.
     */
    @Column(name = "failure_reason")
    private String failureReason;

    /**
     * Timestamp when the request was submitted.
     */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /**
     * Timestamp when the processing of the request started.
     */
    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    /**
     * Timestamp when the request was completed (verified or rejected).
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * The set of documents associated with this KYC request.
     */
    @OneToMany(mappedBy = "kycRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<KycDocument> kycDocuments = new HashSet<>();

    /**
     * The verification results for this KYC request.
     */
    @OneToMany(mappedBy = "kycRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<KycVerificationResult> verificationResults = new HashSet<>();

    /**
     * Optimistic locking version.
     */
    @Version
    private Long version;
}

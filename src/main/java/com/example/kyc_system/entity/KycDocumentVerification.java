package com.example.kyc_system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing the verification status of a specific KYC document.
 */
@Entity
@Table(name = "kyc_document_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycDocumentVerification {

    /**
     * Unique identifier for the document verification record.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The document being verified.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kyc_document_id", nullable = false)
    private KycDocument kycDocument;

    /**
     * The status of the verification (e.g., "PENDING", "VERIFIED", "REJECTED").
     */
    @Column(length = 20, nullable = false)
    private String status;

    /**
     * The reason for rejection, if applicable.
     */
    @Column(name = "rejected_reason")
    private String rejectedReason;

    /**
     * Timestamp when the verification was completed.
     */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /**
     * Timestamp when the verification record was created.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

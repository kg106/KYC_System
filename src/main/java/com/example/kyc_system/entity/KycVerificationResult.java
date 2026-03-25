package com.example.kyc_system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing the final verification result for a KYC request.
 */
@Entity
@Table(name = "kyc_verification_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycVerificationResult {

    /**
     * Unique identifier for the KYC verification result.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The KYC request this result belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kyc_request_id", nullable = false)
    private KycRequest kycRequest;

    /**
     * Score representing the match between user's name and name on document (0-100).
     */
    @Column(name = "name_match_score", precision = 5, scale = 2)
    private BigDecimal nameMatchScore;

    /**
     * Whether the date of birth matches between user record and document.
     */
    @Column(name = "dob_match")
    private Boolean dobMatch;

    /**
     * Whether the document number matches between user record and document.
     */
    @Column(name = "document_number_match")
    private Boolean documentNumberMatch;

    /**
     * The final verification status (e.g., "VERIFIED", "REJECTED").
     */
    @Column(name = "final_status", length = 20, nullable = false)
    private String finalStatus;

    /**
     * Detailed reason for the final decision.
     */
    @Column(name = "decision_reason")
    private String decisionReason;

    /**
     * Timestamp when the verification result was created.
     */
    @CreationTimestamp
    private LocalDateTime createdAt;
}

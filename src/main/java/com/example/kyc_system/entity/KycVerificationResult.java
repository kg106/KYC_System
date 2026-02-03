package com.example.kyc_system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_verification_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycVerificationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kyc_request_id", nullable = false)
    private KycRequest kycRequest;

    @Column(name = "name_match_score", precision = 5, scale = 2)
    private BigDecimal nameMatchScore;

    @Column(name = "dob_match")
    private Boolean dobMatch;

    @Column(name = "document_number_match")
    private Boolean documentNumberMatch;

    @Column(name = "final_status", length = 20, nullable = false)
    private String finalStatus;

    @Column(name = "decision_reason")
    private String decisionReason;

    @CreationTimestamp
    private LocalDateTime createdAt;
}


package com.example.kyc_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(name = "document_type", length = 20, nullable = false)
    private String documentType;

    @Builder.Default
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "kycRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.Set<KycDocument> kycDocuments = new java.util.HashSet<>();

    @OneToMany(mappedBy = "kycRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.Set<KycVerificationResult> verificationResults = new java.util.HashSet<>();
}

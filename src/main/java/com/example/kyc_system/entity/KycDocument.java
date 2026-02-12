package com.example.kyc_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kyc_request_id", nullable = false)
    private KycRequest kycRequest;

    @Column(name = "document_type", length = 20, nullable = false)
    private String documentType;

    @Column(name = "document_number")
    private String documentNumber;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "document_path", nullable = false)
    private String documentPath;

    @Column(name = "document_hash", length = 64, nullable = false)
    private String documentHash;

    @Column(name = "mime_type", length = 50, nullable = false)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Builder.Default
    @Column(name = "is_encrypted", nullable = false)
    private Boolean encrypted = false;

    @OneToMany(mappedBy = "kycDocument", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.Set<KycExtractedData> extractedData = new java.util.HashSet<>();

    @OneToMany(mappedBy = "kycDocument", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.Set<KycDocumentVerification> verifications = new java.util.HashSet<>();
}

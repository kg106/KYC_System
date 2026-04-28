package com.example.kyc_system.entity;

import jakarta.persistence.*;
import com.example.kyc_system.converter.KycEncryptionConverter;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Entity representing an uploaded KYC document.
 */
@Entity
@Table(name = "kyc_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycDocument extends BaseEntity {

    /**
     * Unique identifier for the KYC document.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The KYC request this document belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kyc_request_id", nullable = false)
    private KycRequest kycRequest;

    /**
     * ID of the tenant the document belongs to.
     */
    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    /**
     * Type of document (e.g., "AADHAR", "PAN").
     */
    @Column(name = "document_type", length = 20, nullable = false)
    private String documentType;

    /**
     * Encrypted document number extracted from the document.
     */
    @Convert(converter = KycEncryptionConverter.class)
    @Column(name = "document_number")
    private String documentNumber;

    /**
     * Timestamp when the document was uploaded.
     */
    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    /**
     * Storage path or URL of the document file.
     */
    @Column(name = "document_path", nullable = false)
    private String documentPath;

    /**
     * SHA-256 hash of the document file for integrity check.
     */
    @Column(name = "document_hash", length = 64, nullable = false)
    private String documentHash;

    /**
     * MIME type of the uploaded file.
     */
    @Column(name = "mime_type", length = 50, nullable = false)
    private String mimeType;

    /**
     * Size of the file in bytes.
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * Whether the document file is encrypted at rest.
     */
    @Builder.Default
    @Column(name = "is_encrypted", nullable = false)
    private Boolean encrypted = false;

    /**
     * Data extracted from the document via OCR.
     */
    @OneToMany(mappedBy = "kycDocument", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<KycExtractedData> extractedData = new HashSet<>();

    /**
     * Verification results associated with this document.
     */
    @OneToMany(mappedBy = "kycDocument", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<KycDocumentVerification> verifications = new HashSet<>();
}

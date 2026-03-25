package com.example.kyc_system.entity;

import jakarta.persistence.*;
import com.example.kyc_system.converter.KycEncryptionConverter;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity representing the data extracted from a KYC document via OCR.
 */
@Entity
@Table(name = "kyc_extracted_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycExtractedData {

    /**
     * Unique identifier for the extracted data record.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The document from which this data was extracted.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kyc_document_id", nullable = false)
    private KycDocument kycDocument;

    /**
     * Name extracted from the document.
     */
    @Column(name = "extracted_name")
    private String extractedName;

    /**
     * Date of birth extracted from the document.
     */
    @Column(name = "extracted_dob")
    private LocalDate extractedDob;

    /**
     * Encrypted document number extracted from the document.
     */
    @Convert(converter = KycEncryptionConverter.class)
    @Column(name = "extracted_document_number")
    private String extractedDocumentNumber;

    /**
     * The complete raw response from the OCR service in JSON format.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_ocr_response", columnDefinition = "jsonb")
    private Map<String, Object> rawOcrResponse;

    /**
     * Timestamp when the record was created.
     */
    @CreationTimestamp
    private LocalDateTime createdAt;
}

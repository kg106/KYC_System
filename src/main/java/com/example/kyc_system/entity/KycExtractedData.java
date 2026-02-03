package com.example.kyc_system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "kyc_extracted_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycExtractedData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kyc_document_id", nullable = false)
    private KycDocument kycDocument;

    @Column(name = "extracted_name")
    private String extractedName;

    @Column(name = "extracted_dob")
    private LocalDate extractedDob;

    @Column(name = "extracted_document_number")
    private String extractedDocumentNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_ocr_response", columnDefinition = "jsonb")
    private Map<String, Object> rawOcrResponse;

    @CreationTimestamp
    private LocalDateTime createdAt;
}

package com.example.kyc_system.service.impl;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.entity.KycDocument;
import com.example.kyc_system.entity.KycExtractedData;
import com.example.kyc_system.repository.KycDocumentRepository;
import com.example.kyc_system.repository.KycExtractedDataRepository;
import com.example.kyc_system.service.KycExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Implementation of KycExtractionService.
 * Maps raw OCR fields (name, DOB, document number) to the database entities.
 * Includes defensive parsing for OCR-extracted date strings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KycExtractionServiceImpl implements KycExtractionService {

    private final KycExtractedDataRepository repository;
    private final KycDocumentRepository documentRepository;

    /**
     * Persists OCR results linked to a specific KYC document.
     *
     * @param documentId document ID
     * @param ocrResult raw result DTO from OCR service
     * @return persisted entity
     */
    @Override
    public KycExtractedData save(Long documentId, OcrResult ocrResult) {
        KycDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        KycExtractedData data = KycExtractedData.builder()
                .kycDocument(document)
                .extractedName(ocrResult.getName())
                .extractedDob(safeParseDate(ocrResult.getDob()))
                .extractedDocumentNumber(ocrResult.getDocumentNumber())
                .rawOcrResponse(ocrResult.getRawResponse())
                .createdAt(LocalDateTime.now())
                .build();

        KycExtractedData saved = repository.save(data);
        log.info("Persisted extracted data: docId={}, resultId={}", documentId, saved.getId());
        return saved;
    }

    /**
     * Safely parses an ISO date string (YYYY-MM-DD).
     * Returns null instead of throwing an exception to handle poor OCR quality gracefully.
     *
     * @param dateStr raw string from OCR
     * @return parsed LocalDate or null
     */
    private LocalDate safeParseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.error("Failed to parse extracted DOB: {}. Error: {}", dateStr, e.getMessage());
            return null;
        }
    }
}

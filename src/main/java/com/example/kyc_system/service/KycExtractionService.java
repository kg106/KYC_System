package com.example.kyc_system.service;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.entity.KycExtractedData;

/** 
 * Service interface for persisting OCR-extracted data from KYC documents.
 * Handles the mapping and storage of raw OCR field values into structured entities.
 */
public interface KycExtractionService {
    /**
     * Saves raw OCR results as structured extracted data for a document.
     *
     * @param documentId the document ID
     * @param ocrResult raw fields extracted by OCR
     * @return the saved KycExtractedData entity
     */
    KycExtractedData save(Long documentId, OcrResult ocrResult);
}

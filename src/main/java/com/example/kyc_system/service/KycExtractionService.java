package com.example.kyc_system.service;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.entity.KycExtractedData;

/** Service interface for persisting OCR-extracted data from KYC documents. */
public interface KycExtractionService {
    KycExtractedData save(Long documentId, OcrResult ocrResult);
}

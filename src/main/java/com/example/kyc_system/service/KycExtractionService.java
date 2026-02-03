package com.example.kyc_system.service;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.entity.KycExtractedData;

public interface KycExtractionService {
    KycExtractedData save(Long documentId, OcrResult ocrResult);
}

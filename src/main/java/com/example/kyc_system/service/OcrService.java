package com.example.kyc_system.service;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.enums.DocumentType;

import java.io.File;

/**
 * Service interface for extracting text data from KYC document images using
 * OCR.
 */
public interface OcrService {
    /**
     * Extracts text fields from a document image file.
     *
     * @param file the image file (JPG, PNG, PDF page)
     * @param type the document type (to optimize extraction zones)
     * @return OcrResult containing extracted fields
     */
    OcrResult extract(File file, DocumentType type);
}

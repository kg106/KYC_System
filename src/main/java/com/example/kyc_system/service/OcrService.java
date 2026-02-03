package com.example.kyc_system.service;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.enums.DocumentType;

import java.io.File;

public interface OcrService {
    OcrResult extract(File file, DocumentType type);
}

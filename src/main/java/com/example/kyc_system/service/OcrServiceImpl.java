package com.example.kyc_system.service;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.enums.DocumentType;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;

@Service
public class OcrServiceImpl implements OcrService {

    @Override
    public OcrResult extract(File file, DocumentType type) {
        // Mocking OCR result
        return OcrResult.builder()
                .name("John Doe")
                .dob("1990-01-01")
                .documentNumber("ABCDE1234F")
                .rawResponse(new HashMap<>())
                .build();
    }
}

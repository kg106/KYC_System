package com.example.kyc_system.service;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.entity.KycDocument;
import com.example.kyc_system.entity.KycExtractedData;
import com.example.kyc_system.repository.KycDocumentRepository;
import com.example.kyc_system.repository.KycExtractedDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KycExtractionServiceImpl implements KycExtractionService {

    private final KycExtractedDataRepository repository;
    private final KycDocumentRepository documentRepository;

    @Override
    public KycExtractedData save(Long documentId, OcrResult ocrResult) {
        KycDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        KycExtractedData data = KycExtractedData.builder()
                .kycDocument(document)
                .extractedName(ocrResult.getName())
                .extractedDob(ocrResult.getDob() != null ? LocalDate.parse(ocrResult.getDob()) : null)
                .extractedDocumentNumber(ocrResult.getDocumentNumber())
                .rawOcrResponse(ocrResult.getRawResponse())
                .createdAt(LocalDateTime.now())
                .build();

        return repository.save(data);
    }
}

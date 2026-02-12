package com.example.kyc_system.service;

import com.example.kyc_system.entity.KycDocument;
import com.example.kyc_system.enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;

public interface KycDocumentService {
        KycDocument save(Long requestId, DocumentType documentType, MultipartFile file, String documentNumber);

        boolean isVerified(Long userId, DocumentType documentType, String documentNumber);
}

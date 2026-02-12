package com.example.kyc_system.service;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.entity.*;
import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.enums.KycStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Service
@Transactional
@RequiredArgsConstructor
public class KycOrchestrationService {

        private final UserService userService;
        private final KycRequestService requestService;
        private final KycDocumentService documentService;
        private final OcrService ocrService;
        private final KycExtractionService extractionService;
        private final KycVerificationService verificationService;

        public void processKyc(Long userId,
                        DocumentType documentType,
                        MultipartFile file,
                        String documentNumber) {

                if (documentService.isVerified(userId, documentType, documentNumber)) {
                        throw new RuntimeException("Your " + documentType
                                        + " is already verified. No further action is required.");
                }

                User user = userService.getActiveUser(userId);

                KycRequest request = requestService.createOrReuse(userId);
                requestService.updateStatus(request.getId(), KycStatus.PROCESSING);

                KycDocument document = documentService.save(request.getId(), documentType, file, documentNumber);

                OcrResult ocrResult = ocrService.extract(toFile(file), documentType);

                KycExtractedData extracted = extractionService.save(document.getId(), ocrResult);

                KycVerificationResult result = verificationService.verifyAndSave(request.getId(), user, extracted);

                request.setFailureReason(result.getDecisionReason());
                requestService.updateStatus(
                                request.getId(),
                                KycStatus.valueOf(result.getFinalStatus()));
        }

        private File toFile(MultipartFile multipartFile) {
                try {
                        File tempFile = File.createTempFile("temp-", multipartFile.getOriginalFilename());
                        multipartFile.transferTo(tempFile);
                        tempFile.deleteOnExit();
                        return tempFile;
                } catch (IOException e) {
                        throw new RuntimeException("Failed to convert MultipartFile to File", e);
                }
        }
}

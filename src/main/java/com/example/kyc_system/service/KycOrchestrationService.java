package com.example.kyc_system.service;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.entity.*;
import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.queue.KycQueueService;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.util.KycFileValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycOrchestrationService {

        private final TransactionTemplate transactionTemplate;
        private final KycQueueService queueService;
        private final KycRequestRepository kycRequestRepository; // Direct repo access for CAS
        private final KycRequestService requestService;
        private final KycDocumentService documentService;
        private final OcrService ocrService;
        private final KycExtractionService extractionService;
        private final KycVerificationService verificationService;
        private final KycFileValidator fileValidator;

        @Transactional
        public Long submitKyc(Long userId, DocumentType documentType, MultipartFile file, String documentNumber) {
                fileValidator.validate(file);
                if (documentService.isVerified(userId, documentType, documentNumber)) {
                        throw new RuntimeException("Your " + documentType
                                        + " is already verified. No further action is required.");
                }

                // Synchronous Steps:
                // 1. Create/Reuse Request (Status = SUBMITTED)
                KycRequest request = requestService.createOrReuse(userId, documentType.name());

                // 2. Save File (I/O)
                log.info("Saving KYC document: userId={}, requestId={}, docType={}", userId, request.getId(),
                                documentType);
                documentService.save(request.getId(), documentType, file, documentNumber);

                // 3. Push to Queue
                log.info("Queueing KYC request for async processing: requestId={}", request.getId());
                queueService.push(request.getId());

                return request.getId();
        }

        public void processAsync(Long requestId) {
                // 1. Atomic Check-And-Set (CAS) - Short Transaction
                int rows = transactionTemplate.execute(status -> kycRequestRepository.updateStatusIfPending(requestId,
                                KycStatus.PROCESSING.name(), KycStatus.SUBMITTED.name()));

                if (rows == 0) {
                        log.debug("Request {} already processing or finished, skipping", requestId);
                        return; // Already processed
                }

                log.info("Async processing started for request: {}", requestId);
                // 2. Retrieve Data - Short Transaction
                // We fetch necessary data to perform OCR outside the transaction
                var processingData = transactionTemplate.execute(status -> {
                        KycRequest request = kycRequestRepository.findById(requestId).orElseThrow();
                        // Initialize what we need
                        KycDocument document = request.getKycDocuments().iterator().next();
                        return new Object() {
                                final Long reqId = request.getId();
                                // User not needed here, fetched inside verifyAndSave
                                final String docPath = document.getDocumentPath();
                                final DocumentType docType = DocumentType.valueOf(document.getDocumentType());
                                final Long docId = document.getId();
                        };
                });

                try {
                        // 3. Heavy OCR - NO TRANSACTION HERE
                        // This prevents holding a DB connection during slow I/O
                        OcrResult ocrResult = ocrService.extract(new File(processingData.docPath),
                                        processingData.docType);

                        // 4. Save & Verify - Short Transaction
                        transactionTemplate.execute(status -> {
                                KycExtractedData extracted = extractionService.save(processingData.docId, ocrResult);
                                KycVerificationResult result = verificationService.verifyAndSave(processingData.reqId,
                                                extracted);

                                // Update Request
                                KycRequest request = kycRequestRepository.findById(processingData.reqId).orElseThrow();
                                request.setFailureReason(result.getDecisionReason());
                                requestService.updateStatus(request.getId(),
                                                KycStatus.valueOf(result.getFinalStatus()));
                                return null;
                        });

                } catch (Throwable t) {
                        log.error("Serious error during OCR processing for request {}: {}", requestId, t.getMessage(),
                                        t);
                        // Handle errors in a separate transaction
                        transactionTemplate.execute(status -> {
                                KycRequest request = kycRequestRepository.findById(requestId).orElseThrow();
                                request.setFailureReason("Processing error: " + t.getMessage());
                                requestService.updateStatus(request.getId(), KycStatus.FAILED);
                                log.warn("Request {} marked as FAILED due to processing error", requestId);
                                return null;
                        });
                }
        }

}

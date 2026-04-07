package com.example.kyc_system.service.impl;

import com.example.kyc_system.config.KycProperties;
import com.example.kyc_system.entity.KycDocument;
import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.repository.KycDocumentRepository;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.service.KycDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Implementation of KycDocumentService.
 * Manages the lifecycle of uploaded KYC document files:
 * - Local file system storage (based on KycProperties configuration).
 * - SHA-256 hashing for integrity checks.
 * - Persistence of file metadata in the database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KycDocumentServiceImpl implements KycDocumentService {

    private final KycDocumentRepository repository;
    private final KycRequestRepository requestRepository;
    private final KycProperties kycProperties;

    /**
     * Stores an uploaded file on disk and saves its record in the DB.
     *
     * @param requestId parent KYC request ID
     * @param documentType type of document (PAN/AADHAAR)
     * @param file the multipart file from the controller
     * @param documentNumber the human-readable ID number from the doc
     * @return the saved KycDocument entity
     */
    @Override
    public KycDocument save(Long requestId, DocumentType documentType, MultipartFile file, String documentNumber) {

        KycRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        String path = store(file);
        String hash = calculateHash(file);

        KycDocument doc = KycDocument.builder()
                .kycRequest(request)
                .tenantId(request.getTenantId())
                .documentType(documentType.name())
                .documentNumber(documentNumber)
                .documentPath(path)
                .documentHash(hash)
                .mimeType(file.getContentType())
                .fileSize(file.getSize())
                .uploadedAt(LocalDateTime.now())
                .encrypted(true) // Tag indicating encryption-at-rest is expected (logic pending in Filter/Util)
                .build();

        KycDocument saved = repository.save(doc);
        log.info("KYC document saved: docId={}, requestId={}, type={}", saved.getId(), requestId, documentType);
        return saved;
    }

    /** Checks if a specific document number has already been verified for a user. */
    @Override
    public boolean isVerified(String userId, DocumentType documentType, String documentNumber) {
        return repository.existsByKycRequest_UserIdAndDocumentTypeAndDocumentNumberAndKycRequest_Status(
                java.util.UUID.fromString(userId), documentType.name(), documentNumber, "VERIFIED");
    }

    /**
     * Deletes the physical file associated with a document entity.
     *
     * @param document the document entity to remove
     */
    @Override
    public void deleteDocument(KycDocument document) {
        if (document.getDocumentPath() != null) {
            try {
                Path path = Paths.get(document.getDocumentPath());
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.error("Failed to delete file from disk: {}", document.getDocumentPath(), e);
            }
        }
    }

    /**
     * Internal helper to write MultipartFile bytes to the configured base path.
     * Uses a UUID prefix to prevent collisions.
     */
    private String store(MultipartFile file) {
        try {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path path = Paths.get(kycProperties.getStorage().getBasePath(), fileName);
            Files.createDirectories(path.getParent());
            Files.write(path, file.getBytes());
            return path.toString();
        } catch (IOException e) {
            log.error("Failed to store uploaded file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to store file", e);
        }
    }

    /**
     * Calculates a SHA-256 hex hash of the file content for data integrity.
     */
    private String calculateHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(file.getBytes());
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Failed to calculate hash", e);
        }
    }
}

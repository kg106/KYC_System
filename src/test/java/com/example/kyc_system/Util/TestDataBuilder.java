package com.example.kyc_system.Util;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.entity.*;
import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.enums.KycStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Utility class providing reusable test fixtures for integration tests.
 * All builder methods return pre-configured objects that can be further
 * customized before saving to the repository.
 */
public class TestDataBuilder {

    // ─── User Builders ────────────────────────────────────────────────────────

    /**
     * Builds a standard active user with all required fields.
     * Password is already BCrypt-hashed for "Test@1234"
     */
    public static User buildUser(String email, String name) {
        return User.builder()
                .name(name)
                .email(email)
                .mobileNumber("9876543210")
                .passwordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy") // Test@1234
                .isActive(true)
                .dob(LocalDate.of(1990, 5, 15))
                .build();
    }

    /**
     * Builds a user with a custom date of birth.
     * Useful for DOB mismatch verification tests.
     */
    public static User buildUserWithDob(String email, String name, LocalDate dob) {
        return User.builder()
                .name(name)
                .email(email)
                .mobileNumber("9876543210")
                .passwordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy") // Test@1234
                .isActive(true)
                .dob(dob)
                .build();
    }

    /**
     * Builds an inactive user.
     * Useful for testing access control on deactivated accounts.
     */
    public static User buildInactiveUser(String email, String name) {
        return User.builder()
                .name(name)
                .email(email)
                .mobileNumber("9123456780")
                .passwordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy") // Test@1234
                .isActive(false)
                .dob(LocalDate.of(1985, 3, 20))
                .build();
    }

    // ─── Role Builder ─────────────────────────────────────────────────────────

    public static Role buildRole(String roleName) {
        return Role.builder()
                .name(roleName)
                .build();
    }

    // ─── UserRole Builder ─────────────────────────────────────────────────────

    public static UserRole buildUserRole(User user, Role role) {
        return UserRole.builder()
                .user(user)
                .role(role)
                .build();
    }

    // ─── KycRequest Builders ──────────────────────────────────────────────────

    /**
     * Builds a KYC request in SUBMITTED state.
     * Represents a freshly uploaded request awaiting processing.
     */
    public static KycRequest buildKycRequest(User user, DocumentType documentType) {
        return KycRequest.builder()
                .user(user)
                .documentType(documentType.name())
                .status(KycStatus.SUBMITTED.name())
                .attemptNumber(1)
                .submittedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Builds a KYC request with a specific status.
     * Useful for testing status transitions and re-upload flows.
     */
    public static KycRequest buildKycRequestWithStatus(User user,
            DocumentType documentType,
            KycStatus status) {
        return KycRequest.builder()
                .user(user)
                .documentType(documentType.name())
                .status(status.name())
                .attemptNumber(1)
                .submittedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Builds a FAILED KYC request with a failure reason.
     * Useful for testing re-upload eligibility and attempt counting.
     */
    public static KycRequest buildFailedKycRequest(User user,
            DocumentType documentType,
            String failureReason) {
        return KycRequest.builder()
                .user(user)
                .documentType(documentType.name())
                .status(KycStatus.FAILED.name())
                .attemptNumber(1)
                .failureReason(failureReason)
                .submittedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Builds a VERIFIED KYC request with completed timestamps.
     * Useful for testing duplicate document upload prevention.
     */
    public static KycRequest buildVerifiedKycRequest(User user, DocumentType documentType) {
        return KycRequest.builder()
                .user(user)
                .documentType(documentType.name())
                .status(KycStatus.VERIFIED.name())
                .attemptNumber(1)
                .submittedAt(LocalDateTime.now().minusHours(2))
                .processingStartedAt(LocalDateTime.now().minusHours(1))
                .completedAt(LocalDateTime.now())
                .build();
    }

    // ─── KycDocument Builder ──────────────────────────────────────────────────

    /**
     * Builds a KYC document linked to a request.
     * documentNumber here is the plain-text value before encryption.
     */
    public static KycDocument buildKycDocument(KycRequest request,
            DocumentType documentType,
            String documentNumber) {
        return KycDocument.builder()
                .kycRequest(request)
                .documentType(documentType.name())
                .documentNumber(documentNumber)
                .documentPath("uploads/test-" + documentType.name().toLowerCase() + ".jpg")
                .documentHash("abc123def456abc123def456abc123def456abc123def456abc123def456abc1") // fake SHA-256
                .mimeType("image/jpeg")
                .fileSize(102400L) // 100KB
                .encrypted(true)
                .uploadedAt(LocalDateTime.now())
                .build();
    }

    // ─── KycExtractedData Builders ────────────────────────────────────────────

    /**
     * Builds extracted data where all fields match the user's profile.
     * Use this for VERIFIED flow tests.
     */
    public static KycExtractedData buildMatchingExtractedData(KycDocument document,
            String name,
            LocalDate dob,
            String documentNumber) {
        return KycExtractedData.builder()
                .kycDocument(document)
                .extractedName(name)
                .extractedDob(dob)
                .extractedDocumentNumber(documentNumber)
                .rawOcrResponse(Map.of("text", "mocked ocr output for " + name))
                .build();
    }

    /**
     * Builds extracted data where name does not match.
     * Use this for name-mismatch FAILED flow tests.
     */
    public static KycExtractedData buildNameMismatchExtractedData(KycDocument document,
            LocalDate dob,
            String documentNumber) {
        return KycExtractedData.builder()
                .kycDocument(document)
                .extractedName("Completely Different Name")
                .extractedDob(dob)
                .extractedDocumentNumber(documentNumber)
                .rawOcrResponse(Map.of("text", "name mismatch ocr output"))
                .build();
    }

    /**
     * Builds extracted data where DOB does not match.
     * Use this for DOB-mismatch FAILED flow tests.
     */
    public static KycExtractedData buildDobMismatchExtractedData(KycDocument document,
            String name,
            String documentNumber) {
        return KycExtractedData.builder()
                .kycDocument(document)
                .extractedName(name)
                .extractedDob(LocalDate.of(2000, 1, 1)) // wrong DOB
                .extractedDocumentNumber(documentNumber)
                .rawOcrResponse(Map.of("text", "dob mismatch ocr output"))
                .build();
    }

    /**
     * Builds extracted data where document number does not match.
     * Use this for document-number-mismatch FAILED flow tests.
     */
    public static KycExtractedData buildDocNumberMismatchExtractedData(KycDocument document,
            String name,
            LocalDate dob) {
        return KycExtractedData.builder()
                .kycDocument(document)
                .extractedName(name)
                .extractedDob(dob)
                .extractedDocumentNumber("ZZZZZ9999Z") // wrong document number
                .rawOcrResponse(Map.of("text", "doc number mismatch ocr output"))
                .build();
    }

    // ─── KycVerificationResult Builders ──────────────────────────────────────

    /**
     * Builds a VERIFIED verification result with full match scores.
     */
    public static KycVerificationResult buildVerifiedResult(KycRequest request) {
        return KycVerificationResult.builder()
                .kycRequest(request)
                .nameMatchScore(new java.math.BigDecimal("95.00"))
                .dobMatch(true)
                .documentNumberMatch(true)
                .finalStatus(KycStatus.VERIFIED.name())
                .decisionReason("")
                .build();
    }

    /**
     * Builds a FAILED verification result with a reason.
     */
    public static KycVerificationResult buildFailedResult(KycRequest request, String reason) {
        return KycVerificationResult.builder()
                .kycRequest(request)
                .nameMatchScore(new java.math.BigDecimal("40.00"))
                .dobMatch(false)
                .documentNumberMatch(false)
                .finalStatus(KycStatus.FAILED.name())
                .decisionReason(reason)
                .build();
    }

    // ─── OcrResult Builders ───────────────────────────────────────────────────

    /**
     * Builds an OcrResult where all fields match user's profile.
     * Use this when mocking OcrService for successful verification tests.
     */
    public static OcrResult buildMatchingOcrResult(String name,
            LocalDate dob,
            String documentNumber) {
        return OcrResult.builder()
                .name(name)
                .dob(dob.toString()) // "1990-05-15"
                .documentNumber(documentNumber)
                .rawResponse(Map.of("text", "mocked ocr for " + name))
                .build();
    }

    /**
     * Builds an OcrResult that will cause verification to FAIL.
     * All fields are intentionally wrong.
     */
    public static OcrResult buildMismatchOcrResult() {
        return OcrResult.builder()
                .name("Wrong Person Name")
                .dob("2000-01-01")
                .documentNumber("ZZZZZ9999Z")
                .rawResponse(Map.of("text", "mocked wrong ocr output"))
                .build();
    }

    /**
     * Builds an OcrResult with null fields.
     * Use this to test handling of incomplete OCR extraction.
     */
    public static OcrResult buildEmptyOcrResult() {
        return OcrResult.builder()
                .name(null)
                .dob(null)
                .documentNumber(null)
                .rawResponse(Map.of("text", ""))
                .build();
    }

    // ─── MockMultipartFile Helpers ────────────────────────────────────────────

    /**
     * Creates a valid fake JPEG file for upload tests.
     */
    public static org.springframework.mock.web.MockMultipartFile buildValidImageFile() {
        return new org.springframework.mock.web.MockMultipartFile(
                "file",
                "test-document.jpg",
                "image/jpeg",
                "fake-jpeg-content".getBytes());
    }

    /**
     * Creates a valid fake PDF file for upload tests.
     */
    public static org.springframework.mock.web.MockMultipartFile buildValidPdfFile() {
        return new org.springframework.mock.web.MockMultipartFile(
                "file",
                "test-document.pdf",
                "application/pdf",
                "fake-pdf-content".getBytes());
    }

    /**
     * Creates an invalid file type (text/plain) to trigger validation failure.
     */
    public static org.springframework.mock.web.MockMultipartFile buildInvalidFileType() {
        return new org.springframework.mock.web.MockMultipartFile(
                "file",
                "test-document.txt",
                "text/plain",
                "this is plain text".getBytes());
    }

    /**
     * Creates an empty file to trigger empty file validation failure.
     */
    public static org.springframework.mock.web.MockMultipartFile buildEmptyFile() {
        return new org.springframework.mock.web.MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0] // empty
        );
    }
}
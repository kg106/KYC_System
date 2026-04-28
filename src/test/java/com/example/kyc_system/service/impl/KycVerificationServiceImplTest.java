package com.example.kyc_system.service.impl;

import com.example.kyc_system.entity.*;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.repository.KycVerificationResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for KycVerificationServiceImpl.
 *
 * Tests cover:
 * - Full match → VERIFIED
 * - Name mismatch (below 75% similarity) → FAILED
 * - Fuzzy name match (above 75% similarity) → VERIFIED
 * - DOB mismatch → FAILED
 * - Document number mismatch → FAILED
 * - Stored document number null → FAILED
 * - Multiple mismatches → FAILED with combined reason
 * - Null user name / dob → FAILED
 * - Request not found → RuntimeException
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KycVerificationServiceImpl Unit Tests")
class KycVerificationServiceImplTest {

    @Mock
    private KycVerificationResultRepository repository;
    @Mock
    private KycRequestRepository requestRepository;

    @InjectMocks
    private KycVerificationServiceImpl verificationService;

    private User user;
    private KycRequest request;
    private KycDocument document;
    private KycExtractedData extractedData;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .name("John Doe")
                .dob(LocalDate.of(1990, 1, 1))
                .build();

        request = KycRequest.builder()
                .id(1L)
                .user(user)
                .build();

        document = KycDocument.builder()
                .id(1L)
                .kycRequest(request)
                .documentNumber("DOC12345")
                .documentType("AADHAAR")
                .build();

        extractedData = KycExtractedData.builder()
                .id(1L)
                .kycDocument(document)
                .extractedName("John Doe")
                .extractedDob(LocalDate.of(1990, 1, 1))
                .extractedDocumentNumber("DOC12345")
                .build();
    }

    // ─── VERIFIED Cases ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Should return VERIFIED")
    class VerifiedCases {

        @Test
        @DisplayName("All fields match exactly → VERIFIED with 100% name score")
        void verifyAndSave_AllMatch_ReturnsVerified() {
            when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycVerificationResult result = verificationService.verifyAndSave(1L, extractedData);

            assertEquals(KycStatus.VERIFIED.name(), result.getFinalStatus());
            assertTrue(result.getDobMatch());
            assertTrue(result.getDocumentNumberMatch());
            assertEquals(0, BigDecimal.valueOf(100).compareTo(result.getNameMatchScore()));
        }

        @Test
        @DisplayName("Fuzzy name match above 75% threshold → VERIFIED")
        void verifyAndSave_FuzzyNameMatch_ReturnsVerified() {
            // "John Doe" vs "John Do" — small typo, should still be above 75%
            extractedData.setExtractedName("John Do");

            when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycVerificationResult result = verificationService.verifyAndSave(1L, extractedData);

            assertEquals(KycStatus.VERIFIED.name(), result.getFinalStatus(),
                    "Small typo should still pass 75% similarity threshold");
        }

        @Test
        @DisplayName("Case-insensitive name comparison → VERIFIED")
        void verifyAndSave_CaseInsensitiveName_ReturnsVerified() {
            user.setName("john doe"); // lowercase
            extractedData.setExtractedName("JOHN DOE"); // uppercase

            when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycVerificationResult result = verificationService.verifyAndSave(1L, extractedData);

            assertEquals(KycStatus.VERIFIED.name(), result.getFinalStatus());
        }

        @Test
        @DisplayName("Document number match is case-insensitive and ignores spaces/dashes")
        void verifyAndSave_DocumentNumberNormalized_ReturnsVerified() {
            // Stored: "DOC 12345" → normalized: "DOC12345"
            document.setDocumentNumber("DOC 12345");
            extractedData.setExtractedDocumentNumber("DOC12345");

            when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycVerificationResult result = verificationService.verifyAndSave(1L, extractedData);

            assertEquals(KycStatus.VERIFIED.name(), result.getFinalStatus());
        }
    }

    // ─── FAILED Cases ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Should return FAILED")
    class FailedCases {

        @Test
        @DisplayName("Name similarity below 75% → FAILED with name mismatch reason")
        void verifyAndSave_NameMismatch_ReturnsFailed() {
            extractedData.setExtractedName("Completely Different Person");

            when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycVerificationResult result = verificationService.verifyAndSave(1L, extractedData);

            assertEquals(KycStatus.FAILED.name(), result.getFinalStatus());
            assertTrue(result.getDecisionReason().contains("Name mismatch"),
                    "Reason should mention name mismatch");
            assertTrue(result.getNameMatchScore().compareTo(BigDecimal.valueOf(75)) < 0,
                    "Name score should be below 75%");
        }

        @Test
        @DisplayName("DOB mismatch → FAILED with DOB mismatch reason")
        void verifyAndSave_DobMismatch_ReturnsFailed() {
            extractedData.setExtractedDob(LocalDate.of(2000, 6, 15)); // wrong DOB

            when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycVerificationResult result = verificationService.verifyAndSave(1L, extractedData);

            assertEquals(KycStatus.FAILED.name(), result.getFinalStatus());
            assertFalse(result.getDobMatch());
            assertTrue(result.getDecisionReason().contains("DOB mismatch"),
                    "Reason should mention DOB mismatch");
        }

        @Test
        @DisplayName("Document number mismatch → FAILED")
        void verifyAndSave_DocumentNumberMismatch_ReturnsFailed() {
            extractedData.setExtractedDocumentNumber("DOC99999"); // mismatch

            when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycVerificationResult result = verificationService.verifyAndSave(1L, extractedData);

            assertEquals(KycStatus.FAILED.name(), result.getFinalStatus());
            assertFalse(result.getDocumentNumberMatch());
            assertTrue(result.getDecisionReason().contains("Doc Number mismatch"),
                    "Reason should mention doc number mismatch");
        }

        @Test
        @DisplayName("Stored document number is null → FAILED")
        void verifyAndSave_StoredDocumentNumberNull_ReturnsFailed() {
            document.setDocumentNumber(null);
            extractedData.setExtractedDocumentNumber("DOC12345");

            when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycVerificationResult result = verificationService.verifyAndSave(1L, extractedData);

            assertEquals(KycStatus.FAILED.name(), result.getFinalStatus());
            assertFalse(result.getDocumentNumberMatch());
        }

        @Test
        @DisplayName("Extracted document number is null → FAILED")
        void verifyAndSave_ExtractedDocumentNumberNull_ReturnsFailed() {
            extractedData.setExtractedDocumentNumber(null);

            when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycVerificationResult result = verificationService.verifyAndSave(1L, extractedData);

            assertEquals(KycStatus.FAILED.name(), result.getFinalStatus());
            assertFalse(result.getDocumentNumberMatch());
        }

        @Test
        @DisplayName("User DOB is null → DOB mismatch → FAILED")
        void verifyAndSave_UserDobNull_ReturnsFailed() {
            user.setDob(null);

            when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycVerificationResult result = verificationService.verifyAndSave(1L, extractedData);

            assertEquals(KycStatus.FAILED.name(), result.getFinalStatus());
            assertFalse(result.getDobMatch());
        }

        @Test
        @DisplayName("User name is null → name similarity 0% → FAILED")
        void verifyAndSave_UserNameNull_ReturnsFailed() {
            user.setName(null);

            when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycVerificationResult result = verificationService.verifyAndSave(1L, extractedData);

            assertEquals(KycStatus.FAILED.name(), result.getFinalStatus());
        }

        @Test
        @DisplayName("All three fields mismatch → FAILED with combined reason message")
        void verifyAndSave_AllFieldsMismatch_ReturnsFailedWithCombinedReason() {
            extractedData.setExtractedName("Total Stranger");
            extractedData.setExtractedDob(LocalDate.of(1800, 1, 1));
            extractedData.setExtractedDocumentNumber("WRONGDOC000");

            when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycVerificationResult result = verificationService.verifyAndSave(1L, extractedData);

            assertEquals(KycStatus.FAILED.name(), result.getFinalStatus());
            String reason = result.getDecisionReason();
            assertTrue(reason.contains("Name mismatch"), "Reason must mention name mismatch");
            assertTrue(reason.contains("DOB mismatch"), "Reason must mention DOB mismatch");
            assertTrue(reason.contains("Doc Number mismatch"), "Reason must mention doc mismatch");
        }
    }

    // ─── Error Cases ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("Should throw RuntimeException when KycRequest not found")
        void verifyAndSave_RequestNotFound_ThrowsException() {
            when(requestRepository.findById(99L)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> verificationService.verifyAndSave(99L, extractedData));

            assertTrue(ex.getMessage().contains("Request not found"));
        }
    }
}
package com.example.kyc_system.integration;

import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.service.OcrService;
import com.example.kyc_system.Util.TestDataBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KycUploadIntegrationTest extends BaseIntegrationTest {

    @MockBean
    private OcrService ocrService;

    private User testUser;
    private String userToken;

    @BeforeEach
    void setUp() {
        testUser = createTestUser("kyc@test.com", "John Doe", "ROLE_USER");
        testUser.setDob(LocalDate.of(1990, 5, 15));
        userRepository.save(testUser);
        userToken = generateToken(testUser.getEmail());
    }

    // ── TEST 1: Successful KYC Upload ─────────────────────────────────────────
    @Test
    void uploadKyc_withValidFile_returns202() throws Exception {

        // BEFORE ──────────────────────────────────────────────────────────────
        // OcrResult mockOcr = OcrResult.builder()
        // .name("John Doe")
        // .dob("1990-05-15")
        // .documentNumber("ABCDE1234F")
        // .rawResponse(Map.of("text", "mocked ocr text"))
        // .build();
        //
        // MockMultipartFile file = new MockMultipartFile(
        // "file", "pan.jpg", "image/jpeg",
        // "fake-image-bytes".getBytes()
        // );

        // AFTER ───────────────────────────────────────────────────────────────
        OcrResult mockOcr = TestDataBuilder.buildMatchingOcrResult(
                "John Doe",
                LocalDate.of(1990, 5, 15),
                "ABCDE1234F");

        when(ocrService.extract(any(), eq(DocumentType.PAN)))
                .thenReturn(mockOcr);

        mockMvc.perform(multipart("/api/kyc/upload")
                .file(TestDataBuilder.buildValidImageFile()) // AFTER: was new MockMultipartFile(...)
                .param("userId", testUser.getId().toString())
                .param("documentType", "PAN")
                .param("documentNumber", "ABCDE1234F")
                .header("Authorization", userToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("KYC request submitted successfully"))
                .andExpect(jsonPath("$.requestId").isNumber());
    }

    // ── TEST 2: Invalid File Type ─────────────────────────────────────────────
    @Test
    void uploadKyc_withInvalidFileType_returns400() throws Exception {

        // BEFORE ──────────────────────────────────────────────────────────────
        // MockMultipartFile file = new MockMultipartFile(
        // "file", "doc.txt", "text/plain",
        // "some text content".getBytes()
        // );

        // AFTER ───────────────────────────────────────────────────────────────
        mockMvc.perform(multipart("/api/kyc/upload")
                .file(TestDataBuilder.buildInvalidFileType()) // AFTER: was new MockMultipartFile(...)
                .param("userId", testUser.getId().toString())
                .param("documentType", "PAN")
                .param("documentNumber", "ABCDE1234F")
                .header("Authorization", userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid file type: text/plain"));
    }

    // ── TEST 3: Empty File ────────────────────────────────────────────────────
    @Test
    void uploadKyc_withEmptyFile_returns400() throws Exception {

        // AFTER: TestDataBuilder provides buildEmptyFile() for this case
        mockMvc.perform(multipart("/api/kyc/upload")
                .file(TestDataBuilder.buildEmptyFile()) // NEW TEST using TestDataBuilder
                .param("userId", testUser.getId().toString())
                .param("documentType", "PAN")
                .param("documentNumber", "ABCDE1234F")
                .header("Authorization", userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File is empty"));
    }

    // ── TEST 4: Unauthorized Access (No Token) ────────────────────────────────
    @Test
    void uploadKyc_withoutToken_returns401() throws Exception {

        // BEFORE ──────────────────────────────────────────────────────────────
        // MockMultipartFile file = new MockMultipartFile(
        // "file", "pan.jpg", "image/jpeg", "bytes".getBytes());

        // AFTER ───────────────────────────────────────────────────────────────
        mockMvc.perform(multipart("/api/kyc/upload")
                .file(TestDataBuilder.buildValidImageFile()) // AFTER: was new MockMultipartFile(...)
                .param("userId", testUser.getId().toString())
                .param("documentType", "PAN")
                .param("documentNumber", "ABCDE1234F"))
                // No Authorization header
                .andExpect(status().isUnauthorized());
    }

    // ── TEST 5: Cross-User Access (Security Check) ────────────────────────────
    @Test
    void uploadKyc_forAnotherUser_returns403() throws Exception {
        User anotherUser = createTestUser("other@test.com", "Other User", "ROLE_USER");

        // BEFORE ──────────────────────────────────────────────────────────────
        // MockMultipartFile file = new MockMultipartFile(
        // "file", "pan.jpg", "image/jpeg", "bytes".getBytes());

        // AFTER ───────────────────────────────────────────────────────────────
        mockMvc.perform(multipart("/api/kyc/upload")
                .file(TestDataBuilder.buildValidImageFile()) // AFTER: was new MockMultipartFile(...)
                .param("userId", anotherUser.getId().toString())
                .param("documentType", "PAN")
                .param("documentNumber", "ABCDE1234F")
                .header("Authorization", userToken))
                .andExpect(status().isForbidden());
    }

    // ── TEST 6: Daily Limit (5 Attempts) ─────────────────────────────────────
    @Test
    void uploadKyc_exceedingDailyLimit_returns500() throws Exception {

        // BEFORE ──────────────────────────────────────────────────────────────
        // for (int i = 1; i <= 5; i++) {
        // KycRequest req = new KycRequest();
        // req.setUser(testUser);
        // req.setDocumentType("PAN");
        // req.setStatus("FAILED");
        // req.setAttemptNumber(1);
        // req.setSubmittedAt(LocalDateTime.now());
        // kycRequestRepository.save(req);
        // }

        // AFTER ───────────────────────────────────────────────────────────────
        for (int i = 1; i <= 5; i++) {
            kycRequestRepository.save(
                    TestDataBuilder.buildFailedKycRequest( // AFTER: was manual setter approach
                            testUser,
                            DocumentType.PAN,
                            "Name mismatch on attempt " + i));
        }

        mockMvc.perform(multipart("/api/kyc/upload")
                .file(TestDataBuilder.buildValidImageFile()) // AFTER: was new MockMultipartFile(...)
                .param("userId", testUser.getId().toString())
                .param("documentType", "PAN")
                .param("documentNumber", "ABCDE1234F")
                .header("Authorization", userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Daily KYC attempt limit reached (5). Please try again tomorrow."));
    }

    // ── TEST 7: OCR Mismatch causes FAILED status ─────────────────────────────
    @Test
    void uploadKyc_withOcrMismatch_requestEventuallyFails() throws Exception {

        // AFTER: TestDataBuilder provides buildMismatchOcrResult() for this case
        OcrResult mismatchOcr = TestDataBuilder.buildMismatchOcrResult(); // NEW TEST using TestDataBuilder

        when(ocrService.extract(any(), eq(DocumentType.PAN)))
                .thenReturn(mismatchOcr);

        mockMvc.perform(multipart("/api/kyc/upload")
                .file(TestDataBuilder.buildValidImageFile())
                .param("userId", testUser.getId().toString())
                .param("documentType", "PAN")
                .param("documentNumber", "ABCDE1234F")
                .header("Authorization", userToken))
                .andExpect(status().isAccepted()) // Still accepted at upload time
                .andExpect(jsonPath("$.requestId").isNumber());
    }

    // ── TEST 8: PDF file is also a valid upload ───────────────────────────────
    @Test
    void uploadKyc_withValidPdfFile_returns202() throws Exception {

        // AFTER: TestDataBuilder provides buildValidPdfFile() for this case
        OcrResult mockOcr = TestDataBuilder.buildMatchingOcrResult(
                "John Doe",
                LocalDate.of(1990, 5, 15),
                "ABCDE1234F");

        when(ocrService.extract(any(), eq(DocumentType.PAN)))
                .thenReturn(mockOcr);

        mockMvc.perform(multipart("/api/kyc/upload")
                .file(TestDataBuilder.buildValidPdfFile()) // AFTER: uses PDF builder
                .param("userId", testUser.getId().toString())
                .param("documentType", "PAN")
                .param("documentNumber", "ABCDE1234F")
                .header("Authorization", userToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("KYC request submitted successfully"));
    }
}
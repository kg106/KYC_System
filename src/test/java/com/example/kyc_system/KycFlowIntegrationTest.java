package com.example.kyc_system;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.repository.UserRepository;
import com.example.kyc_system.service.OcrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class KycFlowIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private KycRequestRepository kycRequestRepository;

        @MockBean
        private OcrService ocrService;

        @Autowired
        private com.example.kyc_system.repository.KycDocumentRepository kycDocumentRepository;

        @Autowired
        private com.example.kyc_system.repository.KycVerificationResultRepository kycVerificationResultRepository;

        @Autowired
        private com.example.kyc_system.repository.KycExtractedDataRepository kycExtractedDataRepository;

        @MockBean(name = "securityService")
        private com.example.kyc_system.security.SecurityService securityService;

        private User testUser;

        @BeforeEach
        void setUp() {
                kycVerificationResultRepository.deleteAll();
                kycExtractedDataRepository.deleteAll();
                kycDocumentRepository.deleteAll();
                kycRequestRepository.deleteAll();
                userRepository.deleteAll();

                testUser = User.builder()
                                .name("Integration Test User")
                                .email("test@example.com")
                                .mobileNumber("1234567890")
                                .dob(LocalDate.of(1990, 1, 1))
                                .passwordHash("hashedpassword")
                                .build();
                userRepository.save(testUser);

                when(securityService.canAccessUser(any())).thenReturn(true);
        }

        @Test
        void testFullKycFlow_Success() throws Exception {
                // Mock OCR result to match user data
                OcrResult mockOcrResult = OcrResult.builder()
                                .name("Integration Test User")
                                .dob("1990-01-01")
                                .documentNumber("ABC12345")
                                .rawResponse(new HashMap<>())
                                .build();

                when(ocrService.extract(any(File.class), any(DocumentType.class))).thenReturn(mockOcrResult);

                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "test-doc.jpg",
                                "image/jpeg",
                                "dummy content".getBytes());

                // 1. Upload Document
                mockMvc.perform(multipart("/api/kyc/upload")
                                .file(file)
                                .param("userId", testUser.getId().toString())
                                .param("documentType", "PAN")
                                .param("documentNumber", "ABC12345"))
                                .andExpect(status().isOk());

                // 2. Poll for Status (Verification is synchronous in current impl, but checking
                // DB ensures persistence)
                KycRequest request = kycRequestRepository.findTopByUserIdOrderByCreatedAtDesc(testUser.getId())
                                .orElseThrow(() -> new RuntimeException("Request not found"));

                assertEquals(KycStatus.VERIFIED.name(), request.getStatus());
        }

        @Test
        void testFullKycFlow_Failure_DocumentNumberMismatch() throws Exception {
                // Mock OCR result with mismatched document number
                OcrResult mockOcrResult = OcrResult.builder()
                                .name("Integration Test User")
                                .dob("1990-01-01")
                                .documentNumber("XYZ98765") // Mismatch
                                .rawResponse(new HashMap<>())
                                .build();

                when(ocrService.extract(any(File.class), any(DocumentType.class))).thenReturn(mockOcrResult);

                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "test-doc.jpg",
                                "image/jpeg",
                                "dummy content".getBytes());

                // 1. Upload Document
                mockMvc.perform(multipart("/api/kyc/upload")
                                .file(file)
                                .param("userId", testUser.getId().toString())
                                .param("documentType", "PAN")
                                .param("documentNumber", "ABC12345")) // User claims this
                                .andExpect(status().isOk());

                // 2. Check Status
                KycRequest request = kycRequestRepository.findTopByUserIdOrderByCreatedAtDesc(testUser.getId())
                                .orElseThrow(() -> new RuntimeException("Request not found"));

                assertEquals(KycStatus.FAILED.name(), request.getStatus());
        }

        @Test
        void testKyc_RejectAlreadyVerifiedDocument() throws Exception {
                // 1. Success first
                testFullKycFlow_Success();

                // 2. Try same document again
                MockMultipartFile file = new MockMultipartFile(
                                "file", "test-doc.jpg", "image/jpeg", "dummy".getBytes());

                mockMvc.perform(multipart("/api/kyc/upload")
                                .file(file)
                                .param("userId", testUser.getId().toString())
                                .param("documentType", "PAN")
                                .param("documentNumber", "ABC12345"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testKyc_AllowReSubmissionOfFailedRequest() throws Exception {
                // 1. Fail first
                testFullKycFlow_Failure_DocumentNumberMismatch();

                // 2. Mock success for retry
                OcrResult mockOcrResult = OcrResult.builder()
                                .name("Integration Test User")
                                .dob("1990-01-01")
                                .documentNumber("ABC12345")
                                .rawResponse(new HashMap<>())
                                .build();
                when(ocrService.extract(any(File.class), any(DocumentType.class))).thenReturn(mockOcrResult);

                MockMultipartFile file = new MockMultipartFile(
                                "file", "test-doc.jpg", "image/jpeg", "dummy".getBytes());

                // 3. Re-upload
                mockMvc.perform(multipart("/api/kyc/upload")
                                .file(file)
                                .param("userId", testUser.getId().toString())
                                .param("documentType", "PAN")
                                .param("documentNumber", "ABC12345"))
                                .andExpect(status().isOk());

                // 4. Verify request recycled (attempt number should be 2)
                KycRequest request = kycRequestRepository.findTopByUserIdOrderByCreatedAtDesc(testUser.getId())
                                .orElseThrow(() -> new RuntimeException("Request not found"));
                assertEquals(2, request.getAttemptNumber());
                assertEquals(KycStatus.VERIFIED.name(), request.getStatus());
        }

        @Test
        void testKyc_RejectDuplicateActiveRequest() throws Exception {
                // 1. Manually create a PROCESSING request
                KycRequest activeRequest = new KycRequest();
                activeRequest.setUser(testUser);
                activeRequest.setStatus(KycStatus.PROCESSING.name());
                activeRequest.setAttemptNumber(1);
                activeRequest.setSubmittedAt(java.time.LocalDateTime.now());
                kycRequestRepository.save(activeRequest);

                // 2. Try to upload another document
                MockMultipartFile file = new MockMultipartFile(
                                "file", "test-doc.jpg", "image/jpeg", "dummy".getBytes());

                mockMvc.perform(multipart("/api/kyc/upload")
                                .file(file)
                                .param("userId", testUser.getId().toString())
                                .param("documentType", "AADHAAR")
                                .param("documentNumber", "987654321012"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testKyc_RejectSixthRequestDaily() throws Exception {
                // 1. Create 5 requests for today
                for (int i = 1; i <= 5; i++) {
                        KycRequest request = new KycRequest();
                        request.setUser(testUser);
                        request.setStatus(KycStatus.FAILED.name());
                        request.setAttemptNumber(1);
                        request.setSubmittedAt(java.time.LocalDateTime.now());
                        kycRequestRepository.save(request);
                }

                // 2. Try 6th request
                MockMultipartFile file = new MockMultipartFile(
                                "file", "test-doc.jpg", "image/jpeg", "dummy".getBytes());

                mockMvc.perform(multipart("/api/kyc/upload")
                                .file(file)
                                .param("userId", testUser.getId().toString())
                                .param("documentType", "AADHAAR")
                                .param("documentNumber", "111122223333"))
                                .andExpect(status().isBadRequest());
        }

}

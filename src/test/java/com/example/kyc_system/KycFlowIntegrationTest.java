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
}

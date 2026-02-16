package com.example.kyc_system.service;

import com.example.kyc_system.entity.*;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.repository.KycVerificationResultRepository;
import org.junit.jupiter.api.BeforeEach;
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

@ExtendWith(MockitoExtension.class)
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

    @Test
    void verifyAndSave_AllMatch_ShouldReturnVerified() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(repository.save(any(KycVerificationResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KycVerificationResult result = verificationService.verifyAndSave(1L, user, extractedData);

        assertEquals(KycStatus.VERIFIED.name(), result.getFinalStatus());
        assertTrue(result.getDobMatch());
        assertTrue(result.getDocumentNumberMatch());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(result.getNameMatchScore()));
    }

    @Test
    void verifyAndSave_DocumentNumberMismatch_ShouldReturnFailed() {
        document.setDocumentNumber("DOC12345");
        extractedData.setExtractedDocumentNumber("DOC99999"); // Mismatch

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(repository.save(any(KycVerificationResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KycVerificationResult result = verificationService.verifyAndSave(1L, user, extractedData);

        assertEquals(KycStatus.FAILED.name(), result.getFinalStatus());
        assertFalse(result.getDocumentNumberMatch());
    }

    @Test
    void verifyAndSave_StoredDocumentNumberNull_ShouldReturnFailed() {
        document.setDocumentNumber(null);
        extractedData.setExtractedDocumentNumber("DOC12345");

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(repository.save(any(KycVerificationResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KycVerificationResult result = verificationService.verifyAndSave(1L, user, extractedData);

        assertEquals(KycStatus.FAILED.name(), result.getFinalStatus());
        assertFalse(result.getDocumentNumberMatch());
    }
}

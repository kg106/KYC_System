package com.example.kyc_system.service;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.entity.*;
import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.enums.KycStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class KycOrchestrationServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private KycRequestService requestService;
    @Mock
    private KycDocumentService documentService;
    @Mock
    private OcrService ocrService;
    @Mock
    private KycExtractionService extractionService;
    @Mock
    private KycVerificationService verificationService;

    @InjectMocks
    private KycOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void processKyc_ShouldFlowThroughAllServices_WhenSuccessful() throws IOException {
        Long userId = 1L;
        DocumentType type = DocumentType.PAN;
        String docNumber = "PAN123";
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "content".getBytes());

        when(documentService.isVerified(userId, type, docNumber)).thenReturn(false);
        when(userService.getActiveUser(userId)).thenReturn(new User());
        when(requestService.createOrReuse(userId, type.name())).thenReturn(KycRequest.builder().id(1L).build());

        KycDocument mockDocument = new KycDocument();
        mockDocument.setId(1L);
        when(documentService.save(anyLong(), any(), any(), any())).thenReturn(mockDocument);

        when(ocrService.extract(any(), any())).thenReturn(new OcrResult());
        when(extractionService.save(anyLong(), any())).thenReturn(new KycExtractedData());
        when(verificationService.verifyAndSave(anyLong(), any(), any())).thenReturn(
                KycVerificationResult.builder().finalStatus("VERIFIED").build());

        orchestrationService.processKyc(userId, type, file, docNumber);

        verify(documentService).isVerified(userId, type, docNumber);
        verify(userService).getActiveUser(userId);
        verify(requestService).createOrReuse(userId, type.name());
        verify(requestService, times(2)).updateStatus(anyLong(), any());
        verify(documentService).save(eq(1L), eq(type), any(), eq(docNumber));
        verify(ocrService).extract(any(), eq(type));
        verify(extractionService).save(eq(1L), any());
        verify(verificationService).verifyAndSave(anyLong(), any(), any());
    }

    @Test
    void processKyc_ShouldThrowException_WhenAlreadyVerified() {
        Long userId = 1L;
        DocumentType type = DocumentType.PAN;
        String docNumber = "PAN123";
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "content".getBytes());

        when(documentService.isVerified(userId, type, docNumber)).thenReturn(true);

        assertThrows(RuntimeException.class, () -> orchestrationService.processKyc(userId, type, file, docNumber));
        verifyNoInteractions(userService, requestService, ocrService);
    }

    @Test
    void processKyc_ShouldFail_WhenUserServiceThrowsException() {
        Long userId = 1L;
        DocumentType type = DocumentType.PAN;
        String docNumber = "PAN123";
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "content".getBytes());

        when(documentService.isVerified(userId, type, docNumber)).thenReturn(false);
        when(userService.getActiveUser(userId)).thenThrow(new RuntimeException("User not found"));

        assertThrows(RuntimeException.class, () -> orchestrationService.processKyc(userId, type, file, docNumber));
    }
}

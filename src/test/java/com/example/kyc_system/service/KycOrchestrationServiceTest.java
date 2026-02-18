package com.example.kyc_system.service;

import com.example.kyc_system.entity.*;
import com.example.kyc_system.enums.DocumentType;
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
    @Mock
    private com.example.kyc_system.queue.KycQueueService queueService;
    @Mock
    private com.example.kyc_system.repository.KycRequestRepository kycRequestRepository;

    @InjectMocks
    private KycOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void submitKyc_ShouldQueueRequest_WhenSuccessful() throws IOException {
        Long userId = 1L;
        DocumentType type = DocumentType.PAN;
        String docNumber = "PAN123";
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "content".getBytes());

        when(documentService.isVerified(userId, type, docNumber)).thenReturn(false);
        // when(userService.getActiveUser(userId)).thenReturn(new User()); // Not used
        // in submitKyc anymore
        when(requestService.createOrReuse(userId, type.name())).thenReturn(KycRequest.builder().id(100L).build());

        KycDocument mockDocument = new KycDocument();
        mockDocument.setId(1L);
        when(documentService.save(anyLong(), any(), any(), any())).thenReturn(mockDocument);

        // Act
        orchestrationService.submitKyc(userId, type, file, docNumber);

        // Assert
        verify(documentService).isVerified(userId, type, docNumber);
        // verify(userService).getActiveUser(userId); // Not used
        verify(requestService).createOrReuse(userId, type.name());
        verify(documentService).save(eq(100L), eq(type), any(), eq(docNumber));
        verify(queueService).push(100L); // Verify Pushed to Queue

        // Verify Async parts are NOT called synchronously
        verifyNoInteractions(ocrService);
        verifyNoInteractions(extractionService);
        verifyNoInteractions(verificationService);
    }

    @Test
    void processKyc_ShouldThrowException_WhenAlreadyVerified() {
        Long userId = 1L;
        DocumentType type = DocumentType.PAN;
        String docNumber = "PAN123";
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "content".getBytes());

        when(documentService.isVerified(userId, type, docNumber)).thenReturn(true);

        assertThrows(RuntimeException.class, () -> orchestrationService.submitKyc(userId, type, file, docNumber));
        verifyNoInteractions(userService, requestService, ocrService);
    }

    /*
     * @Test
     * void processKyc_ShouldFail_WhenUserServiceThrowsException() {
     * // ... (Disabled as logic moved to async or requestService)
     * }
     */
}

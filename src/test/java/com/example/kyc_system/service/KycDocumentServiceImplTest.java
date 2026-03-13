package com.example.kyc_system.service;

import com.example.kyc_system.entity.KycDocument;
import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.repository.KycDocumentRepository;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.util.KycFileValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KycDocumentServiceImpl Unit Tests")
class KycDocumentServiceImplTest {

    @Mock
    private KycDocumentRepository repository;
    @Mock
    private KycRequestRepository requestRepository;
    @Mock
    private KycFileValidator fileValidator;

    @InjectMocks
    private KycDocumentServiceImpl documentService;

    private KycRequest mockRequest;
    private MockMultipartFile validJpeg;
    private MockMultipartFile validPdf;

    @BeforeEach
    void setUp() {
        mockRequest = KycRequest.builder().id(1L).build();
        validJpeg = new MockMultipartFile("file", "pan.jpg", "image/jpeg", "fake-image".getBytes());
        validPdf = new MockMultipartFile("file", "aadhaar.pdf", "application/pdf", "fake-pdf".getBytes());
    }

    // ─── save() ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("Should call validator, fetch request, and persist document")
        void save_ValidFile_PersistsDocument() {
            when(requestRepository.findById(1L)).thenReturn(Optional.of(mockRequest));
            when(repository.save(any())).thenReturn(KycDocument.builder().id(10L).build());

            KycDocument result = documentService.save(1L, DocumentType.PAN, validJpeg, "ABCDE1234F");

            assertNotNull(result);
            assertEquals(10L, result.getId());
            verify(fileValidator).validate(validJpeg);
            verify(requestRepository).findById(1L);
            verify(repository).save(any(KycDocument.class));
        }

        @Test
        @DisplayName("Should populate all required fields on saved entity")
        void save_ValidFile_SetsAllFields() {
            when(requestRepository.findById(1L)).thenReturn(Optional.of(mockRequest));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            documentService.save(1L, DocumentType.PAN, validJpeg, "ABCDE1234F");

            ArgumentCaptor<KycDocument> captor = ArgumentCaptor.forClass(KycDocument.class);
            verify(repository).save(captor.capture());

            KycDocument saved = captor.getValue();
            assertEquals("PAN", saved.getDocumentType());
            assertEquals("ABCDE1234F", saved.getDocumentNumber());
            assertEquals("image/jpeg", saved.getMimeType());
            assertEquals(validJpeg.getSize(), saved.getFileSize());
            assertTrue(saved.getEncrypted());
            assertNotNull(saved.getUploadedAt());
            assertNotNull(saved.getDocumentPath());
            assertNotNull(saved.getDocumentHash());
        }

        @Test
        @DisplayName("SHA-256 hash must be exactly 64 lowercase hex characters")
        void save_CalculatesValidSha256Hash() {
            when(requestRepository.findById(1L)).thenReturn(Optional.of(mockRequest));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            documentService.save(1L, DocumentType.PAN, validJpeg, "ABCDE1234F");

            ArgumentCaptor<KycDocument> captor = ArgumentCaptor.forClass(KycDocument.class);
            verify(repository).save(captor.capture());

            String hash = captor.getValue().getDocumentHash();
            assertNotNull(hash);
            assertEquals(64, hash.length(), "SHA-256 hex string must be 64 chars");
            assertTrue(hash.matches("[a-f0-9]+"), "Hash must be lowercase hex");
        }

        @Test
        @DisplayName("Two different file contents must produce different hashes")
        void save_DifferentFileContents_ProduceDifferentHashes() {
            when(requestRepository.findById(anyLong())).thenReturn(Optional.of(mockRequest));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            MockMultipartFile file1 = new MockMultipartFile("file", "a.jpg", "image/jpeg", "content-A".getBytes());
            MockMultipartFile file2 = new MockMultipartFile("file", "b.jpg", "image/jpeg", "content-B".getBytes());

            documentService.save(1L, DocumentType.PAN, file1, "DOC1");
            documentService.save(1L, DocumentType.PAN, file2, "DOC2");

            ArgumentCaptor<KycDocument> captor = ArgumentCaptor.forClass(KycDocument.class);
            verify(repository, times(2)).save(captor.capture());

            assertNotEquals(
                    captor.getAllValues().get(0).getDocumentHash(),
                    captor.getAllValues().get(1).getDocumentHash());
        }

        @Test
        @DisplayName("Should throw RuntimeException when KycRequest not found")
        void save_RequestNotFound_ThrowsException() {
            when(requestRepository.findById(99L)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> documentService.save(99L, DocumentType.PAN, validJpeg, "ABCDE1234F"));

            assertTrue(ex.getMessage().contains("Request not found"));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Should NOT proceed to saving if fileValidator throws")
        void save_ValidatorRejects_NeverCallsRepository() {
            doThrow(new IllegalArgumentException("Invalid file type: text/plain"))
                    .when(fileValidator).validate(any());

            MockMultipartFile textFile = new MockMultipartFile("file", "doc.txt", "text/plain", "data".getBytes());

            assertThrows(IllegalArgumentException.class,
                    () -> documentService.save(1L, DocumentType.PAN, textFile, "ABCDE1234F"));

            verify(requestRepository, never()).findById(any());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Should correctly handle AADHAAR document type")
        void save_AadhaarType_SetsCorrectDocumentType() {
            when(requestRepository.findById(1L)).thenReturn(Optional.of(mockRequest));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            documentService.save(1L, DocumentType.AADHAAR, validPdf, "123456789012");

            ArgumentCaptor<KycDocument> captor = ArgumentCaptor.forClass(KycDocument.class);
            verify(repository).save(captor.capture());
            assertEquals("AADHAAR", captor.getValue().getDocumentType());
            assertEquals("application/pdf", captor.getValue().getMimeType());
        }
    }

    // ─── isVerified() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isVerified()")
    class IsVerifiedTests {

        @Test
        @DisplayName("Should return true when repository finds a VERIFIED document")
        void isVerified_DocumentVerified_ReturnsTrue() {
            when(repository.existsByKycRequest_User_IdAndDocumentTypeAndDocumentNumberAndKycRequest_Status(
                    1L, "PAN", "ABCDE1234F", "VERIFIED")).thenReturn(true);

            assertTrue(documentService.isVerified(1L, DocumentType.PAN, "ABCDE1234F"));
        }

        @Test
        @DisplayName("Should return false when no VERIFIED document found")
        void isVerified_NotVerified_ReturnsFalse() {
            when(repository.existsByKycRequest_User_IdAndDocumentTypeAndDocumentNumberAndKycRequest_Status(
                    1L, "PAN", "ABCDE1234F", "VERIFIED")).thenReturn(false);

            assertFalse(documentService.isVerified(1L, DocumentType.PAN, "ABCDE1234F"));
        }

        @Test
        @DisplayName("Should call repository with DocumentType.name() string, not enum")
        void isVerified_PassesDocumentTypeNameString_ToRepository() {
            documentService.isVerified(2L, DocumentType.AADHAAR, "999988887777");

            verify(repository).existsByKycRequest_User_IdAndDocumentTypeAndDocumentNumberAndKycRequest_Status(
                    2L, "AADHAAR", "999988887777", "VERIFIED");
        }
    }

    // ─── deleteDocument() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteDocument()")
    class DeleteDocumentTests {

        @Test
        @DisplayName("Should delete file from filesystem when path exists")
        void deleteDocument_ExistingFile_DeletesSuccessfully() throws IOException {
            Path tempFile = Files.createTempFile("kyc-test", ".jpg");
            assertTrue(Files.exists(tempFile));

            KycDocument doc = KycDocument.builder().id(1L).documentPath(tempFile.toString()).build();
            documentService.deleteDocument(doc);

            assertFalse(Files.exists(tempFile), "File should be deleted from filesystem");
        }

        @Test
        @DisplayName("Should not throw if document path is null")
        void deleteDocument_NullPath_NoException() {
            KycDocument doc = KycDocument.builder().id(1L).documentPath(null).build();
            assertDoesNotThrow(() -> documentService.deleteDocument(doc));
        }

        @Test
        @DisplayName("Should not throw if file does not exist on disk")
        void deleteDocument_NonExistentPath_NoException() {
            KycDocument doc = KycDocument.builder()
                    .id(1L)
                    .documentPath("/tmp/kyc-nonexistent-xyz123.jpg")
                    .build();
            assertDoesNotThrow(() -> documentService.deleteDocument(doc));
        }
    }
}
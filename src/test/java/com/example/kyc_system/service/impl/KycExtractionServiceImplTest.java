package com.example.kyc_system.service.impl;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.entity.KycDocument;
import com.example.kyc_system.entity.KycExtractedData;
import com.example.kyc_system.repository.KycDocumentRepository;
import com.example.kyc_system.repository.KycExtractedDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KycExtractionServiceImpl Unit Tests")
class KycExtractionServiceImplTest {

    @Mock
    private KycExtractedDataRepository repository;
    @Mock
    private KycDocumentRepository documentRepository;

    @InjectMocks
    private KycExtractionServiceImpl extractionService;

    private KycDocument mockDocument;

    @BeforeEach
    void setUp() {
        mockDocument = KycDocument.builder()
                .id(1L)
                .documentType("PAN")
                .documentNumber("ABCDE1234F")
                .build();
    }

    // ─── save() — Happy Path ───────────────────────────────────────────────────

    @Nested
    @DisplayName("save() — happy path")
    class SaveHappyPathTests {

        @Test
        @DisplayName("Should map all OcrResult fields to KycExtractedData and persist")
        void save_FullOcrResult_MapsAllFieldsCorrectly() {
            OcrResult ocrResult = OcrResult.builder()
                    .name("John Doe")
                    .dob("1990-01-15")
                    .documentNumber("ABCDE1234F")
                    .rawResponse(Map.of("text", "raw ocr output"))
                    .build();

            when(documentRepository.findById(1L)).thenReturn(Optional.of(mockDocument));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycExtractedData result = extractionService.save(1L, ocrResult);

            assertNotNull(result);
            assertEquals("John Doe", result.getExtractedName());
            assertEquals(LocalDate.of(1990, 1, 15), result.getExtractedDob());
            assertEquals("ABCDE1234F", result.getExtractedDocumentNumber());
            assertEquals(mockDocument, result.getKycDocument());
            assertNotNull(result.getCreatedAt());
            assertNotNull(result.getRawOcrResponse());
        }

        @Test
        @DisplayName("Should save and return the persisted entity from repository")
        void save_ValidInput_ReturnsSavedEntity() {
            OcrResult ocrResult = OcrResult.builder()
                    .name("Jane Doe")
                    .dob("1995-06-20")
                    .documentNumber("DOC123")
                    .rawResponse(Map.of("text", "ocr text"))
                    .build();

            KycExtractedData savedEntity = KycExtractedData.builder().id(99L).build();

            when(documentRepository.findById(1L)).thenReturn(Optional.of(mockDocument));
            when(repository.save(any())).thenReturn(savedEntity);

            KycExtractedData result = extractionService.save(1L, ocrResult);

            assertEquals(99L, result.getId());
            verify(repository).save(any(KycExtractedData.class));
        }

        @Test
        @DisplayName("Should set createdAt timestamp at time of saving")
        void save_SetsCreatedAt() {
            OcrResult ocrResult = OcrResult.builder()
                    .name("Karan")
                    .dob("1992-10-14")
                    .documentNumber("123456789012")
                    .rawResponse(Map.of())
                    .build();

            when(documentRepository.findById(1L)).thenReturn(Optional.of(mockDocument));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycExtractedData result = extractionService.save(1L, ocrResult);

            assertNotNull(result.getCreatedAt());
        }
    }

    // ─── save() — DOB Parsing Edge Cases ──────────────────────────────────────

    @Nested
    @DisplayName("save() — DOB parsing edge cases")
    class DobParsingTests {

        @Test
        @DisplayName("Should parse valid ISO date string 'yyyy-MM-dd' correctly")
        void save_ValidIsoDate_ParsedCorrectly() {
            OcrResult ocrResult = OcrResult.builder()
                    .name("Alice")
                    .dob("1988-03-22")
                    .documentNumber("DOC1")
                    .rawResponse(Map.of())
                    .build();

            when(documentRepository.findById(1L)).thenReturn(Optional.of(mockDocument));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycExtractedData result = extractionService.save(1L, ocrResult);

            assertEquals(LocalDate.of(1988, 3, 22), result.getExtractedDob());
        }

        @Test
        @DisplayName("Should store null DOB when OcrResult dob is null")
        void save_NullDob_StoresNullExtractedDob() {
            OcrResult ocrResult = OcrResult.builder()
                    .name("Bob")
                    .dob(null)
                    .documentNumber("DOC2")
                    .rawResponse(Map.of())
                    .build();

            when(documentRepository.findById(1L)).thenReturn(Optional.of(mockDocument));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycExtractedData result = extractionService.save(1L, ocrResult);

            assertNull(result.getExtractedDob(), "Null dob should be stored as null, not throw");
        }

        @Test
        @DisplayName("Should store null DOB when OCR returns blank date string")
        void save_BlankDobString_StoresNullExtractedDob() {
            OcrResult ocrResult = OcrResult.builder()
                    .name("Charlie")
                    .dob("   ")
                    .documentNumber("DOC3")
                    .rawResponse(Map.of())
                    .build();

            when(documentRepository.findById(1L)).thenReturn(Optional.of(mockDocument));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycExtractedData result = extractionService.save(1L, ocrResult);

            assertNull(result.getExtractedDob(), "Blank dob string should result in null");
        }

        @Test
        @DisplayName("Should store null DOB when OCR returns an unparseable date format")
        void save_InvalidDateFormat_StoresNullExtractedDob() {
            OcrResult ocrResult = OcrResult.builder()
                    .name("Dave")
                    .dob("31-Dec-1990") // Not ISO format
                    .documentNumber("DOC4")
                    .rawResponse(Map.of())
                    .build();

            when(documentRepository.findById(1L)).thenReturn(Optional.of(mockDocument));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            // Should NOT throw — safeParseDate handles gracefully
            assertDoesNotThrow(() -> extractionService.save(1L, ocrResult));

            ArgumentCaptor<KycExtractedData> captor = ArgumentCaptor.forClass(KycExtractedData.class);
            verify(repository).save(captor.capture());
            assertNull(captor.getValue().getExtractedDob());
        }
    }

    // ─── save() — Error Cases ──────────────────────────────────────────────────

    @Nested
    @DisplayName("save() — error cases")
    class ErrorCaseTests {

        @Test
        @DisplayName("Should throw RuntimeException when document not found")
        void save_DocumentNotFound_ThrowsRuntimeException() {
            when(documentRepository.findById(99L)).thenReturn(Optional.empty());

            OcrResult ocrResult = OcrResult.builder()
                    .name("Ghost")
                    .dob("2000-01-01")
                    .documentNumber("GHOST123")
                    .rawResponse(Map.of())
                    .build();

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> extractionService.save(99L, ocrResult));

            assertTrue(ex.getMessage().contains("Document not found"));
            verify(repository, never()).save(any());
        }
    }

    // ─── save() — Null OcrResult Fields ───────────────────────────────────────

    @Nested
    @DisplayName("save() — null OCR fields stored gracefully")
    class NullOcrFieldTests {

        @Test
        @DisplayName("Should store null name when OcrResult name is null")
        void save_NullName_StoresNullExtractedName() {
            OcrResult ocrResult = OcrResult.builder()
                    .name(null)
                    .dob("1990-01-01")
                    .documentNumber("DOC123")
                    .rawResponse(Map.of())
                    .build();

            when(documentRepository.findById(1L)).thenReturn(Optional.of(mockDocument));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycExtractedData result = extractionService.save(1L, ocrResult);

            assertNull(result.getExtractedName());
        }

        @Test
        @DisplayName("Should store null document number when OcrResult documentNumber is null")
        void save_NullDocumentNumber_StoresNullExtractedDocNumber() {
            OcrResult ocrResult = OcrResult.builder()
                    .name("Someone")
                    .dob("1990-01-01")
                    .documentNumber(null)
                    .rawResponse(Map.of())
                    .build();

            when(documentRepository.findById(1L)).thenReturn(Optional.of(mockDocument));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            KycExtractedData result = extractionService.save(1L, ocrResult);

            assertNull(result.getExtractedDocumentNumber());
        }
    }
}
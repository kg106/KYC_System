package com.example.kyc_system.service.impl;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.enums.DocumentType;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.TesseractException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OcrServiceImpl.
 *
 * We spy on OcrServiceImpl to override getTesseractInstance() — this prevents
 * the real Tesseract binary from being needed during tests.
 *
 * Tests cover:
 * - PAN extraction (name, DOB, document number)
 * - Aadhaar extraction (standard, year-only DOB, with-spaces number)
 * - DOB fallback patterns (DD/MM/YYYY, DD-MM-YYYY)
 * - Document type validation (wrong doc type → exception)
 * - Blank/null OCR text → no exception, null fields
 * - Tesseract throws → RuntimeException propagated
 * - Generic fallback for unrecognized doc number patterns
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OcrServiceImpl Unit Tests")
class OcrServiceImplTest {

    private OcrServiceImpl ocrService;
    private ITesseract tesseract;

    @BeforeEach
    void setUp() {
        ocrService = spy(new OcrServiceImpl());
        ReflectionTestUtils.setField(ocrService, "dataPath", "/usr/share/tesseract-ocr/5/tessdata");
        tesseract = mock(ITesseract.class);
    }

    // ─── PAN Card ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PAN card extraction")
    class PanExtractionTests {

        @Test
        @DisplayName("Should extract name, DOB, and PAN number from standard PAN card OCR")
        void extract_StandardPanCard_ExtractsAllFields() throws TesseractException {
            doReturn(tesseract).when(ocrService).getTesseractInstance();
            String ocrText = "INCOME TAX DEPARTMENT\n" +
                    "GOVT. OF INDIA\n" +
                    "KARAN GONDALIYA\n" +
                    "Father's Name\n" +
                    "HIMMATBHAI GONDALIYA\n" +
                    "DOB: 01/01/1990\n" +
                    "ABCDE1234F";
            when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

            OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.PAN);

            assertNotNull(result);
            assertEquals("KARAN GONDALIYA", result.getName());
            assertEquals("1990-01-01", result.getDob());
            assertEquals("ABCDE1234F", result.getDocumentNumber());
        }

        @Test
        @DisplayName("Should extract name above Father's Name label (primary PAN logic)")
        void extract_PanWithFathersNameLabel_ExtractsNameCorrectly() throws TesseractException {
            doReturn(tesseract).when(ocrService).getTesseractInstance();
            String ocrText = "Permanent Account Number Card\n" +
                    "JOHN DOE\n" +
                    "Father's Name\n" +
                    "RICHARD DOE\n" +
                    "DOB: 15/06/1985\n" +
                    "PQRST9876Z";
            when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

            OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.PAN);

            assertEquals("JOHN DOE", result.getName());
            assertEquals("PQRST9876Z", result.getDocumentNumber());
        }

        @Test
        @DisplayName("Should throw when Aadhaar text is submitted as PAN")
        void extract_AadhaarTextAsPan_ThrowsWithSpecificMessage() throws TesseractException {
            doReturn(tesseract).when(ocrService).getTesseractInstance();
            String ocrText = "Government of India\nUnique Identification Authority of India\nAadhaar\n1234 5678 9012";
            when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> ocrService.extract(new File("dummy.jpg"), DocumentType.PAN));

            assertTrue(ex.getMessage().contains("Aadhaar card, but PAN was expected"));
        }

        @Test
        @DisplayName("Should throw with generic message for unrecognized document as PAN")
        void extract_UnrecognizedDocAsPan_ThrowsGenericMessage() throws TesseractException {
            doReturn(tesseract).when(ocrService).getTesseractInstance();
            when(tesseract.doOCR(any(File.class))).thenReturn("Random garbage text 12345");

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> ocrService.extract(new File("dummy.jpg"), DocumentType.PAN));

            assertTrue(ex.getMessage().contains("Could not verify this is a PAN card"));
        }
    }

    // ─── Aadhaar Card ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Aadhaar card extraction")
    class AadhaarExtractionTests {

        @Test
        @DisplayName("Should extract name, DOB (DD/MM/YYYY), and 12-digit Aadhaar number")
        void extract_StandardAadhaar_ExtractsAllFields() throws TesseractException {
            doReturn(tesseract).when(ocrService).getTesseractInstance();
            String ocrText = "Government of India\n" +
                    "Karan Gondaliya\n" +
                    "DOB: 14/10/1992\n" +
                    "Male\n" +
                    "1234 5678 9012";
            when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

            OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR);

            assertNotNull(result);
            assertEquals("Karan Gondaliya", result.getName());
            assertEquals("1992-10-14", result.getDob());
            assertEquals("123456789012", result.getDocumentNumber()); // spaces stripped
        }

        @Test
        @DisplayName("Should extract year-only DOB (Year of Birth: YYYY) and append -01-01")
        void extract_AadhaarYearOnlyDob_ReturnsYearWithJanFirst() throws TesseractException {
            doReturn(tesseract).when(ocrService).getTesseractInstance();
            String ocrText = "Government of India\n" +
                    "Karan Gondaliya\n" +
                    "Year of Birth: 1990\n" +
                    "Male\n" +
                    "1234 5678 9012";
            when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

            OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR);

            assertEquals("1990-01-01", result.getDob());
            assertEquals("123456789012", result.getDocumentNumber());
        }

        @Test
        @DisplayName("Should extract name from line above DOB label (positional fallback)")
        void extract_NameFallbackAboveDob_ExtractsCorrectly() throws TesseractException {
            doReturn(tesseract).when(ocrService).getTesseractInstance();
            String ocrText = "Government of India\nAadhaar\nJohn Doe\nDOB: 14-10-1992\nMale\n9876543210123";
            when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

            OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR);

            assertEquals("John Doe", result.getName());
        }

        @Test
        @DisplayName("Should throw when PAN text is submitted as Aadhaar")
        void extract_PanTextAsAadhaar_ThrowsWithSpecificMessage() throws TesseractException {
            doReturn(tesseract).when(ocrService).getTesseractInstance();
            String ocrText = "Income Tax Department\nPermanent Account Number Card\nABCDE1234F";
            when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR));

            assertTrue(ex.getMessage().contains("PAN card, but Aadhaar was expected"));
        }

        @Test
        @DisplayName("Should throw with generic message for unrecognized document as Aadhaar")
        void extract_UnrecognizedDocAsAadhaar_ThrowsGenericMessage() throws TesseractException {
            doReturn(tesseract).when(ocrService).getTesseractInstance();
            when(tesseract.doOCR(any(File.class))).thenReturn("Random garbage text 12345");

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR));

            assertTrue(ex.getMessage().contains("Could not verify this is an Aadhaar card"));
        }
    }

    // ─── DOB Parsing Patterns ─────────────────────────────────────────────────

    @Nested
    @DisplayName("DOB extraction edge cases")
    class DobExtractionTests {

        @Test
        @DisplayName("Should parse DD-MM-YYYY format via fallback pattern")
        void extract_DobWithDashSeparator_ParsedCorrectly() throws TesseractException {
            doReturn(tesseract).when(ocrService).getTesseractInstance();
            String ocrText = "Permanent Account Number Card\n" +
                    "GOVT. OF INDIA\n" +
                    "JANE SMITH\n" +
                    "Father's Name\n" +
                    "RICHARD SMITH\n" +
                    "Date of Birth: 25-12-1988\n" +
                    "ABCDE1234F";
            when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

            OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.PAN);

            assertEquals("1988-12-25", result.getDob());
        }

        @Test
        @DisplayName("Should return null DOB when no recognizable date pattern found in text")
        void extract_NoDobInText_ReturnsNullDob() throws TesseractException {
            doReturn(tesseract).when(ocrService).getTesseractInstance();
            // Valid Aadhaar but no date at all
            String ocrText = "Government of India\nMale\n1234 5678 9012";
            when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

            OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR);

            assertNull(result.getDob(), "DOB should be null when no date pattern is found");
        }
    }

    // ─── Blank / Empty OCR Text ───────────────────────────────────────────────

    @Nested
    @DisplayName("Blank OCR text")
    class BlankOcrTextTests {

        @Test
        @DisplayName("Should return OcrResult with null fields when OCR returns blank text")
        void extract_BlankOcrText_ReturnsNullFields() throws TesseractException {
            doReturn(tesseract).when(ocrService).getTesseractInstance();
            when(tesseract.doOCR(any(File.class))).thenReturn("   ");

            // validateDocumentType exits early on blank — no exception thrown
            OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.PAN);

            assertNull(result.getName());
            assertNull(result.getDob());
            assertNull(result.getDocumentNumber());
        }
    }

    // ─── Tesseract Native Exception ────────────────────────────────────────────

    @Nested
    @DisplayName("Tesseract native exception handling")
    class TesseractExceptionTests {

        @Test
        @DisplayName("Should wrap TesseractException in RuntimeException with 'OCR failed' message")
        void extract_TesseractThrows_WrapsInRuntimeException() throws TesseractException {
            doReturn(tesseract).when(ocrService).getTesseractInstance();
            when(tesseract.doOCR(any(File.class))).thenThrow(new TesseractException("native error"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> ocrService.extract(new File("dummy.jpg"), DocumentType.PAN));

            assertTrue(ex.getMessage().contains("OCR failed"));
            assertInstanceOf(TesseractException.class, ex.getCause());
        }
    }

    // ─── rawResponse ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rawResponse population")
    class RawResponseTests {

        @Test
        @DisplayName("Should populate rawResponse map with 'text' key containing full OCR output")
        void extract_RawResponseContainsFullText() throws TesseractException {
            doReturn(tesseract).when(ocrService).getTesseractInstance();
            String ocrText = "Permanent Account Number Card\nGOVT. OF INDIA\nJOHN DOE\nFather's Name\nJANE DOE\nDOB: 01/01/1990\nABCDE1234F";
            when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

            OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.PAN);

            assertNotNull(result.getRawResponse());
            assertTrue(result.getRawResponse().containsKey("text"));
            assertEquals(ocrText, result.getRawResponse().get("text"));
        }
    }
}
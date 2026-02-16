package com.example.kyc_system.service;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.enums.DocumentType;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.TesseractException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcrServiceImplTest {

    @Spy
    @InjectMocks
    private OcrServiceImpl ocrService;

    @Mock
    private ITesseract tesseract;

    @Test
    void extract_ShouldParseFieldsCorrectly() throws TesseractException {
        // Mock getTesseractInstance to return mock tesseract
        doReturn(tesseract).when(ocrService).getTesseractInstance();

        String ocrText = "Government of India\nAadhaar\nName: John Doe\nDOB: 01-01-1990\nNo: ABC12345";
        when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

        OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR);

        assertNotNull(result);
        assertEquals("John Doe", result.getName());
        assertEquals("1990-01-01", result.getDob());
        assertEquals("ABC12345", result.getDocumentNumber());
        assertEquals(ocrText, result.getRawResponse().get("text"));
    }

    @Test
    void extract_ShouldHandleAlreadyFormattedDate() throws TesseractException {
        doReturn(tesseract).when(ocrService).getTesseractInstance();

        String ocrText = "Government of India\nAadhaar\nName: John Doe\nDOB: 1990-01-01\nNo: ABC12345";
        when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

        OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR);

        assertNotNull(result);
        assertEquals("1990-01-01", result.getDob());
    }

    @Test
    void extract_ShouldHandleUSFormatDate() throws TesseractException {
        doReturn(tesseract).when(ocrService).getTesseractInstance();

        String ocrText = "Government of India\nAadhaar\nName: John Doe\nDOB: 10/14/1992\nNo: ABC12345";
        when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

        OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR);

        assertNotNull(result);
        assertEquals("1992-10-14", result.getDob());
    }

    @Test
    void extract_ShouldHandleFlexibleLabels() throws TesseractException {
        doReturn(tesseract).when(ocrService).getTesseractInstance();

        String ocrText = "Government of India\nAadhaar\nFull Name: John Doe\nDate of Birth: 14-10-1992\nDOC NO: ID-8829-0041-X";
        when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

        OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR);

        assertNotNull(result);
        assertEquals("John Doe", result.getName());
        assertEquals("1992-10-14", result.getDob());
        assertEquals("ID-8829-0041-X", result.getDocumentNumber());
    }

    @Test
    void extract_ShouldHandleNameFallback() throws TesseractException {
        doReturn(tesseract).when(ocrService).getTesseractInstance();

        // Name is on the line above DOB, without a "Name:" label
        String ocrText = "Government of India\nAadhaar\nJohn Doe\nDOB: 14-10-1992\nNo: ABC12345";
        when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

        OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR);

        assertNotNull(result);
        assertEquals("John Doe", result.getName());
    }

    @Test
    void extract_ShouldHandlePanCard() throws TesseractException {
        doReturn(tesseract).when(ocrService).getTesseractInstance();

        String ocrText = "INCOME TAX DEPARTMENT\nGOVT. OF INDIA\nKARAN GONDALIYA\nFATHER'S NAME\nHIMMATBHAI GONDALIYA\nDOB: 01/01/1990\nPAN: ABCDE1234F";
        when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

        OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.PAN);

        assertNotNull(result);
        assertEquals("KARAN GONDALIYA", result.getName());
        assertEquals("1990-01-01", result.getDob());
        assertEquals("ABCDE1234F", result.getDocumentNumber());
    }

    @Test
    void extract_ShouldHandleAadhaarStandard() throws TesseractException {
        doReturn(tesseract).when(ocrService).getTesseractInstance();

        String ocrText = "Government of India\nKaran Gondaliya\nDOB: 14/10/1992\nMale\n1234 5678 9012";
        when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

        OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR);

        assertNotNull(result);
        assertEquals("Karan Gondaliya", result.getName());
        assertEquals("1992-10-14", result.getDob());
        assertEquals("123456789012", result.getDocumentNumber());
    }

    @Test
    void extract_ShouldHandleAadhaarYearOnly() throws TesseractException {
        doReturn(tesseract).when(ocrService).getTesseractInstance();

        String ocrText = "Government of India\nKaran Gondaliya\nYear of Birth: 1990\nMale\n1234 5678 9012";
        when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

        OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR);

        assertNotNull(result);
        assertEquals("Karan Gondaliya", result.getName());
        assertEquals("1990-01-01", result.getDob());
        assertEquals("123456789012", result.getDocumentNumber());
    }

    @Test
    void extract_ShouldThrowException_WhenPanMismatch() throws TesseractException {
        doReturn(tesseract).when(ocrService).getTesseractInstance();

        // OCR text clearly looks like Aadhaar, but requested as PAN
        String ocrText = "Government of India\nUnique Identification Authority of India\nAadhaar\n1234 5678 9012";
        when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> ocrService.extract(new File("dummy.jpg"), DocumentType.PAN));

        assertTrue(exception.getMessage().contains("Uploaded document appears to be an Aadhaar card"));
    }

    @Test
    void extract_ShouldThrowException_WhenAadhaarMismatch() throws TesseractException {
        doReturn(tesseract).when(ocrService).getTesseractInstance();

        // OCR text clearly looks like PAN, but requested as Aadhaar
        String ocrText = "Income Tax Department\nPermanent Account Number Card\nABCDE1234F";
        when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR));

        assertTrue(exception.getMessage().contains("Uploaded document appears to be a PAN card"));
    }

    @Test
    void extract_ShouldThrowException_WhenUnverifiableDocument() throws TesseractException {
        doReturn(tesseract).when(ocrService).getTesseractInstance();

        // OCR text has random garbage
        String ocrText = "Random text that doesn't look like any ID card.";
        when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> ocrService.extract(new File("dummy.jpg"), DocumentType.PAN));

        assertTrue(exception.getMessage().contains("Could not verify this is a PAN card"));
    }
}

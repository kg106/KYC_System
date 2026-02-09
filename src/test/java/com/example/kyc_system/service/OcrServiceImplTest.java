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

        String ocrText = "Name: John Doe\nDOB: 01-01-1990\nNo: ABC12345";
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

        String ocrText = "Name: John Doe\nDOB: 1990-01-01\nNo: ABC12345";
        when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

        OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR);

        assertNotNull(result);
        assertEquals("1990-01-01", result.getDob());
    }

    @Test
    void extract_ShouldHandleUSFormatDate() throws TesseractException {
        doReturn(tesseract).when(ocrService).getTesseractInstance();

        String ocrText = "Name: John Doe\nDOB: 10/14/1992\nNo: ABC12345";
        when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

        OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR);

        assertNotNull(result);
        assertEquals("1992-10-14", result.getDob());
    }

    @Test
    void extract_ShouldHandleFlexibleLabels() throws TesseractException {
        doReturn(tesseract).when(ocrService).getTesseractInstance();

        String ocrText = "Full Name: John Doe\nDate of Birth: 14-10-1992\nDOC NO: ID-8829-0041-X";
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
        String ocrText = "John Doe\nDOB: 14-10-1992\nNo: ABC12345";
        when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

        OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.AADHAAR);

        assertNotNull(result);
        assertEquals("John Doe", result.getName());
    }
}

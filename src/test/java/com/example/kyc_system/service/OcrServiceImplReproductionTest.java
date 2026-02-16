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
class OcrServiceImplReproductionTest {

    @Spy
    @InjectMocks
    private OcrServiceImpl ocrService;

    @Mock
    private ITesseract tesseract;

    @Test
    void extract_ShouldParsePanFromUserProvidedData() throws TesseractException {
        // Mock getTesseractInstance to return mock tesseract
        doReturn(tesseract).when(ocrService).getTesseractInstance();

        // Data provided by user
        String ocrText = "INCOME TAX DEPARTMENT © @@8_—«- GOVT. OF INDIA\n" +
                "\n" +
                "wort centers | BRR\n" +
                "\n" +
                "Permanent Account Number Card ee\n" +
                "\\ABCDE1234F ae\n" +
                "\n" +
                "Lar ee\n" +
                "\\DOE JOHN ee\n" +
                "\n" +
                "RGR Bweowa |\n" +
                "tae od ahr\n" +
                "01/06/2001 ‘FETT [Signature\n";

        when(tesseract.doOCR(any(File.class))).thenReturn(ocrText);

        OcrResult result = ocrService.extract(new File("dummy.jpg"), DocumentType.PAN);

        assertNotNull(result);
        assertEquals("ABCDE1234F", result.getDocumentNumber());
        assertEquals("DOE JOHN", result.getName());
        assertEquals("2001-06-01", result.getDob());
    }
}

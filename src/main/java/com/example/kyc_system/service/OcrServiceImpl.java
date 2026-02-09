package com.example.kyc_system.service;

import com.example.kyc_system.dto.OcrResult;
import com.example.kyc_system.enums.DocumentType;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrServiceImpl implements OcrService {

    @Value("${tesseract.datapath}")
    private String dataPath;

    protected ITesseract getTesseractInstance() {
        Tesseract t = new Tesseract();
        t.setDatapath(dataPath);
        return t;
    }

    @Override
    public OcrResult extract(File file, DocumentType type) {
        try {
            ITesseract tesseract = getTesseractInstance();
            tesseract.setLanguage("eng");

            String result = tesseract.doOCR(file);
            Map<String, Object> raw = new HashMap<>();
            raw.put("text", result);

            return OcrResult.builder()
                    .name(extractName(result))
                    .dob(extractDob(result))
                    .documentNumber(extractDocumentNumber(result))
                    .rawResponse(raw)
                    .build();
        } catch (TesseractException e) {
            throw new RuntimeException("OCR failed", e);
        }
    }

    private String extractName(String text) {
        // Look for "Name" or "Full Name" followed by whitespace/colon
        Pattern p = Pattern.compile("(?:Name|Full Name)[:\\s]+([A-Za-z .]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        // Fallback: If "NAME" is not found, attempt to find a line before DOB that
        // looks like a name
        String[] lines = text.split("\\n");
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase();
            if (lower.contains("dob") || lower.contains("date of birth")) {
                if (i > 0)
                    return lines[i - 1].trim();
            }
        }
        return null;
    }

    private String extractDob(String text) {
        // Supports "DOB", "Date of Birth", "Birth Date"
        Pattern p = Pattern.compile("(?:DOB|Date of Birth|Birth Date)[:\\s]+(\\d{2,4}[-/]\\d{2}[-/]\\d{2,4})",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return convertDate(m.group(1));
        }
        return null;
    }

    private String extractDocumentNumber(String text) {
        // Supports "No", "Number", "ID NO", "DOC NO"
        Pattern p = Pattern.compile("(?:No|Number|ID\\s*NO|DOC\\s*NO)[:\\s]+([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private String convertDate(String date) {
        if (date == null)
            return null;
        String[] parts = date.split("[-/]");
        if (parts.length == 3) {
            // YYYY-MM-DD
            if (parts[0].length() == 4) {
                return date.replace('/', '-');
            }

            try {
                int v0 = Integer.parseInt(parts[0]);
                int v1 = Integer.parseInt(parts[1]);

                if (v1 > 12) { // MM/DD/YYYY -> YYYY-MM-DD
                    return parts[2] + "-" + String.format("%02d", v0) + "-" + String.format("%02d", v1);
                } else { // DD/MM/YYYY -> YYYY-MM-DD
                    return parts[2] + "-" + String.format("%02d", v1) + "-" + String.format("%02d", v0);
                }
            } catch (NumberFormatException e) {
                return date;
            }
        }
        return date;
    }
}

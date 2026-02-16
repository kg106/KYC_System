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
            validateDocumentType(result, type);
            Map<String, Object> raw = new HashMap<>();
            raw.put("text", result);

            return OcrResult.builder()
                    .name(extractName(result, type))
                    .dob(extractDob(result, type))
                    .documentNumber(extractDocumentNumber(result, type))
                    .rawResponse(raw)
                    .build();
        } catch (TesseractException e) {
            throw new RuntimeException("OCR failed", e);
        }
    }

    private void validateDocumentType(String text, DocumentType type) {
        if (text == null || text.isBlank()) {
            return;
        }

        String lowerText = text.toLowerCase();
        if (type == DocumentType.PAN) {
            boolean hasPanKeywords = lowerText.contains("income tax department") ||
                    lowerText.contains("permanent account number card") ||
                    lowerText.contains("govt. of india");

            if (!hasPanKeywords) {
                // Check for Aadhaar keywords to give a better error message
                if (lowerText.contains("authority of india") || lowerText.contains("aadhaar")
                        || lowerText.contains("vid :") || lowerText.contains("government of india")) {
                    throw new RuntimeException(
                            "Invalid document: Uploaded document appears to be an Aadhaar card, but PAN was expected.");
                }
                throw new RuntimeException(
                        "Invalid document: Could not verify this is a PAN card. Please ensure the image is clear.");
            }
        } else if (type == DocumentType.AADHAAR) {
            boolean hasAadhaarKeywords = lowerText.contains("authority of india") ||
                    lowerText.contains("aadhaar") ||
                    lowerText.contains("vid :") ||
                    lowerText.contains("male") ||
                    lowerText.contains("female") ||
                    lowerText.contains("enrollment no") ||
                    lowerText.contains("government of india");

            if (!hasAadhaarKeywords) {
                // Check for PAN keywords to give a better error message
                if (lowerText.contains("income tax department")
                        || lowerText.contains("permanent account number card")) {
                    throw new RuntimeException(
                            "Invalid document: Uploaded document appears to be a PAN card, but Aadhaar was expected.");
                }
                throw new RuntimeException(
                        "Invalid document: Could not verify this is an Aadhaar card. Please ensure the image is clear.");
            }
        }
    }

    private String extractName(String text, DocumentType type) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String[] lines = text.split("\\n");
        if (type == DocumentType.PAN) {
            // PAN card name is usually in all caps, above "Father's Name"
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.toLowerCase().contains("father's name") || line.toLowerCase().contains("fathers name")) {
                    if (i > 0) {
                        String candidate = cleanName(lines[i - 1].trim());
                        if (isValidName(candidate))
                            return candidate;
                    }
                }
            }

            // Fallback for PAN: Look for lines that look like names (all caps, alphabetic)
            // usually after "Permanent Account Number Card" and before DOB
            boolean foundTitle = false;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.toLowerCase().contains("permanent account number card")) {
                    foundTitle = true;
                    continue;
                }
                if (foundTitle) {
                    // Skip the PAN number line if it appears right after title
                    if (trimmed.matches("[A-Z0-9\\\\ ]+") && (trimmed.contains("1") || trimmed.contains("2"))) {
                        continue;
                    }
                    String candidate = cleanName(trimmed);
                    if (isValidName(candidate))
                        return candidate;
                }
            }
        } else if (type == DocumentType.AADHAAR) {
            // Aadhaar name is usually at the top, before "DOB" or "Year of Birth"
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.toLowerCase().contains("dob") || line.toLowerCase().contains("birth")) {
                    if (i > 0) {
                        String candidate = cleanName(lines[i - 1].trim());
                        if (isValidName(candidate))
                            return candidate;
                    }
                }
            }
        }

        // Try labeled regex across entire text
        Pattern pLabeled = Pattern.compile("(?m)^(?:Name|Full Name)[:\\s]+([A-Za-z .]+)$", Pattern.CASE_INSENSITIVE);
        Matcher mLabeled = pLabeled.matcher(text);
        if (mLabeled.find()) {
            return mLabeled.group(1).trim();
        }

        return null;
    }

    private boolean isValidName(String name) {
        if (name == null || name.length() < 5)
            return false;
        // Ignore lines with digits or some specific symbols
        if (name.matches(".*[0-9@#%&*+=].*"))
            return false;

        // Count alphabetic characters vs total characters
        long alphaCount = name.chars().filter(Character::isAlphabetic).count();
        if ((double) alphaCount / name.length() < 0.7)
            return false;

        // Ignore common header/footer text or small garbage fragments
        String lower = name.toLowerCase();
        if (lower.contains("india") || lower.contains("department") ||
                lower.contains("income") || lower.contains("card") ||
                lower.contains("permanent") || lower.contains("account") ||
                lower.contains("signature"))
            return false;

        // Blacklist known OCR noise
        if (lower.equals("lar ee"))
            return false;

        // Names on IDs usually have at least two words OR one reasonably long word
        if (!name.contains(" ") && name.length() < 6)
            return false;

        return true;
    }

    private String cleanName(String name) {
        if (name == null)
            return null;
        // Remove common labels and noise like backslashes often seen in OCR
        // Also remove leading/trailing noise characters that might be misread as
        // letters
        // Specifically handle trailing fragments like " ee", " ae" which are common in
        // ID scans
        return name.replaceAll("(?i)^(?:Name|Full Name)[:\\s]+", "")
                .replaceAll("[\\\\|©_—«-]", "")
                .replaceAll("\\s+[a-z]{2,3}$", "") // Remove trailing lowercase noise like 'ee', 'ae'
                .replaceAll("^[^A-Za-z]+|[^A-Za-z]+$", "")
                .trim();
    }

    private String extractDob(String text, DocumentType type) {
        // Look for common date patterns with labels
        Pattern p = Pattern.compile(
                "(?:DOB|Date of Birth|Birth Date|Year of Birth)[:\\s]+(\\d{2,4}[-/]\\d{2}[-/]\\d{2,4}|\\d{4})",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            String dob = m.group(1);
            if (dob.length() == 4)
                return dob + "-01-01"; // Just year of birth
            return convertDate(dob);
        }

        // Fallback: Look for any date pattern DD/MM/YYYY or DD-MM-YYYY
        Pattern pFallback = Pattern.compile("(\\d{2}[-/]\\d{2}[-/]\\d{4})");
        Matcher mFallback = pFallback.matcher(text);
        if (mFallback.find()) {
            return convertDate(mFallback.group(1));
        }

        // Fallback for Aadhaar "Year of Birth: YYYY"
        if (type == DocumentType.AADHAAR) {
            Pattern pYear = Pattern.compile("(\\d{4})");
            Matcher mYear = pYear.matcher(text);
            if (mYear.find()) {
                return mYear.group(1) + "-01-01";
            }
        }
        return null;
    }

    private String extractDocumentNumber(String text, DocumentType type) {
        if (type == DocumentType.PAN) {
            // ABCDE1234F
            Pattern p = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]{1}");
            Matcher m = p.matcher(text);
            if (m.find())
                return m.group(0);
        } else if (type == DocumentType.AADHAAR) {
            // 1234 5678 9012
            Pattern p = Pattern.compile("\\d{4}\\s?\\d{4}\\s?\\d{4}");
            Matcher m = p.matcher(text);
            if (m.find())
                return m.group(0).replaceAll("\\s", "");
        }

        // Generic Fallback
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

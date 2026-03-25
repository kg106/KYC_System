package com.example.kyc_system.util;

/**
 * Utility for masking sensitive identifiers (e.g., Aadhaar, PAN).
 * Ensures only the last 4 digits are visible for display purposes.
 */
public class MaskingUtil {

    /**
     * Masks the input string, leaving only the last 4 characters visible.
     * Example: "1234567890" becomes "******7890".
     *
     * @param documentNumber the raw document number
     * @return the masked string
     */
    public static String maskDocumentNumber(String documentNumber) {
        if (documentNumber == null || documentNumber.length() <= 4) {
            return documentNumber;
        }

        int length = documentNumber.length();
        String lastFour = documentNumber.substring(length - 4);
        String maskedPart = "*".repeat(length - 4);

        return maskedPart + lastFour;
    }
}

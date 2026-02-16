package com.example.kyc_system.util;

public class MaskingUtil {

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

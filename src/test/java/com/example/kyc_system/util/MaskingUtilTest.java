package com.example.kyc_system.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MaskingUtil Unit Tests")
class MaskingUtilTest {

    @ParameterizedTest
    @CsvSource({
            "1234567890, ******7890",
            "ABC12345, ****2345",
            "1234, 1234",
            "12, 12",
            "'', ''"
    })
    @DisplayName("Should mask document number correctly leaving only last 4 chars")
    void maskDocumentNumber_VariousInputs_ReturnsMasked(String input, String expected) {
        if (input == null)
            input = ""; // Handle null if needed, though CsvSource handles it differently
        assertEquals(expected, MaskingUtil.maskDocumentNumber(input));
    }

    @Test
    @DisplayName("Should return null if input is null")
    void maskDocumentNumber_NullInput_ReturnsNull() {
        assertNull(MaskingUtil.maskDocumentNumber(null));
    }
}

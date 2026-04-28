package com.example.kyc_system.converter;

import com.example.kyc_system.util.EncryptionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KycEncryptionConverter Unit Tests")
class KycEncryptionConverterTest {

    @Mock
    private EncryptionUtil encryptionUtil;

    @InjectMocks
    private KycEncryptionConverter converter;

    @Test
    @DisplayName("Should encrypt valid string for database column")
    void convertToDatabaseColumn_WithValidString_ReturnsEncryptedString() {
        String original = "sensitive-data";
        String encrypted = "encrypted-data";
        when(encryptionUtil.encrypt(original)).thenReturn(encrypted);

        String result = converter.convertToDatabaseColumn(original);

        assertEquals(encrypted, result);
        verify(encryptionUtil).encrypt(original);
    }

    @Test
    @DisplayName("Should return null when converting null to database column")
    void convertToDatabaseColumn_WithNull_ReturnsNull() {
        String result = converter.convertToDatabaseColumn(null);

        assertNull(result);
        verify(encryptionUtil, never()).encrypt(anyString());
    }

    @Test
    @DisplayName("Should return empty string when converting empty string to database column")
    void convertToDatabaseColumn_WithEmptyString_ReturnsEmptyString() {
        String result = converter.convertToDatabaseColumn("");

        assertEquals("", result);
        verify(encryptionUtil, never()).encrypt(anyString());
    }

    @Test
    @DisplayName("Should decrypt valid encrypted string from database")
    void convertToEntityAttribute_WithValidEncryptedString_ReturnsDecryptedString() {
        String encrypted = "encrypted-data";
        String decrypted = "sensitive-data";
        when(encryptionUtil.decrypt(encrypted)).thenReturn(decrypted);

        String result = converter.convertToEntityAttribute(encrypted);

        assertEquals(decrypted, result);
        verify(encryptionUtil).decrypt(encrypted);
    }

    @Test
    @DisplayName("Should fallback to original string if decryption fails (legacy data)")
    void convertToEntityAttribute_WithDecryptionFailure_ReturnsOriginalString() {
        String legacyData = "plain-text-legacy-data";
        when(encryptionUtil.decrypt(legacyData)).thenThrow(new RuntimeException("Decryption failed"));

        String result = converter.convertToEntityAttribute(legacyData);

        assertEquals(legacyData, result);
        verify(encryptionUtil).decrypt(legacyData);
    }

    @Test
    @DisplayName("Should return null when converting null entity attribute")
    void convertToEntityAttribute_WithNull_ReturnsNull() {
        String result = converter.convertToEntityAttribute(null);

        assertNull(result);
        verify(encryptionUtil, never()).decrypt(anyString());
    }

    @Test
    @DisplayName("Should return empty string when converting empty entity attribute")
    void convertToEntityAttribute_WithEmptyString_ReturnsEmptyString() {
        String result = converter.convertToEntityAttribute("");

        assertEquals("", result);
        verify(encryptionUtil, never()).decrypt(anyString());
    }
}

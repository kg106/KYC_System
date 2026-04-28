package com.example.kyc_system.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptionUtil Unit Tests")
class EncryptionUtilTest {

    private EncryptionUtil encryptionUtil;
    private static final String TEST_SECRET = "test-secret-key-32-characters-!!"; // 32 chars

    @BeforeEach
    void setUp() {
        encryptionUtil = new EncryptionUtil();
        ReflectionTestUtils.setField(encryptionUtil, "secretKey", TEST_SECRET);
    }

    @Test
    @DisplayName("Should encrypt and decrypt correctly (round trip)")
    void encryptDecrypt_RoundTrip_ReturnsOriginalValue() {
        String original = "sensitive-data-123";
        String encrypted = encryptionUtil.encrypt(original);
        String decrypted = encryptionUtil.decrypt(encrypted);

        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("Should be deterministic (same input, same secret = same output)")
    void encrypt_SameInput_ReturnsSameOutput() {
        String data = "data";
        String encrypted1 = encryptionUtil.encrypt(data);
        String encrypted2 = encryptionUtil.encrypt(data);

        assertEquals(encrypted1, encrypted2);
    }

    @Test
    @DisplayName("Should throw RuntimeException on invalid decryption input")
    void decrypt_InvalidInput_ThrowsException() {
        assertThrows(RuntimeException.class, () -> encryptionUtil.decrypt("not-base64-and-not-encrypted"));
    }
}

package com.example.kyc_system.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PasswordUtil Unit Tests")
class PasswordUtilTest {

    @Test
    @DisplayName("Should correctly hash and verify password")
    void hashAndCheckPassword_ValidInput_ReturnsTrue() {
        String password = "mySecurePassword123";
        String hashed = PasswordUtil.hashPassword(password);

        assertNotNull(hashed);
        assertNotEquals(password, hashed);
        assertTrue(PasswordUtil.checkPassword(password, hashed));
    }

    @Test
    @DisplayName("Should return false for incorrect password")
    void checkPassword_IncorrectPassword_ReturnsFalse() {
        String password = "mySecurePassword123";
        String hashed = PasswordUtil.hashPassword(password);

        assertFalse(PasswordUtil.checkPassword("wrongPassword", hashed));
    }

    @Test
    @DisplayName("Different hashes should be generated for the same password due to salt")
    void hashPassword_SameInput_ReturnsDifferentHashes() {
        String password = "samePassword";
        String hashed1 = PasswordUtil.hashPassword(password);
        String hashed2 = PasswordUtil.hashPassword(password);

        assertNotEquals(hashed1, hashed2);
        assertTrue(PasswordUtil.checkPassword(password, hashed1));
        assertTrue(PasswordUtil.checkPassword(password, hashed2));
    }
}

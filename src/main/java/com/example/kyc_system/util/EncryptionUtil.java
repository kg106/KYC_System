package com.example.kyc_system.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility for AES-256 encryption and decryption of sensitive strings.
 * Uses CBC mode with PKCS5 padding and a fixed IV for deterministic behavior
 * (necessary for consistent indexing/searching of encrypted fields).
 *
 * The secret key is injected from application properties.
 */
@Component
@Slf4j
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    // Fixed IV for deterministic encryption
    private static final byte[] FIXED_IV = "1234567812345678".getBytes(StandardCharsets.UTF_8);

    @Value("${app.encryption-secret}")
    private String secretKey;

    /**
     * Encrypts a plain-text string using AES-256 CBC.
     *
     * @param strToEncrypt the raw string to protect
     * @return Base64 encoded ciphertext
     */
    public String encrypt(String strToEncrypt) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(getKeyBytes(), "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(FIXED_IV);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("Encryption error: {}", e.getMessage());
            throw new RuntimeException("Error while encrypting: " + e.getMessage());
        }
    }

    /**
     * Decrypts a Base64 encoded ciphertext back to plain-text.
     *
     * @param strToDecrypt the Base64 ciphertext
     * @return the original plain-text string
     */
    public String decrypt(String strToDecrypt) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(getKeyBytes(), "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(FIXED_IV);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)));
        } catch (Exception e) {
            log.debug("Decryption error: {}", e.getMessage());
            throw new RuntimeException("Error while decrypting: " + e.getMessage());
        }
    }

    private byte[] getKeyBytes() {
        byte[] keyBytes = new byte[32]; // AES-256
        byte[] secretBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(secretBytes, 0, keyBytes, 0, Math.min(secretBytes.length, 32));
        return keyBytes;
    }
}

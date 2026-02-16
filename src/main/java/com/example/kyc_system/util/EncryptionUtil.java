package com.example.kyc_system.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final byte[] FIXED_IV = "1234567812345678".getBytes(StandardCharsets.UTF_8); // Fixed IV for
                                                                                                // deterministic
                                                                                                // encryption

    @Value("${app.encryption-secret}")
    private String secretKey;

    public String encrypt(String strToEncrypt) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(getKeyBytes(), "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(FIXED_IV);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Error while encrypting: " + e.toString());
        }
    }

    public String decrypt(String strToDecrypt) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(getKeyBytes(), "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(FIXED_IV);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)));
        } catch (Exception e) {
            throw new RuntimeException("Error while decrypting: " + e.toString());
        }
    }

    private byte[] getKeyBytes() {
        byte[] keyBytes = new byte[32]; // AES-256
        byte[] secretBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(secretBytes, 0, keyBytes, 0, Math.min(secretBytes.length, 32));
        return keyBytes;
    }
}

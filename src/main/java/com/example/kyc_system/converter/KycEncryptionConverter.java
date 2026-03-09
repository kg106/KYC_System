package com.example.kyc_system.converter;

import com.example.kyc_system.util.EncryptionUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter that automatically encrypts/decrypts sensitive string
 * fields
 * (e.g., document numbers) when writing to and reading from the database.
 * Used via @Convert annotation on entity fields like
 * KycDocument.documentNumber.
 *
 * @Lazy on EncryptionUtil avoids circular dependency during Spring context
 *       initialization.
 */
@Component
@Converter
public class KycEncryptionConverter implements AttributeConverter<String, String> {

    private final EncryptionUtil encryptionUtil;

    public KycEncryptionConverter(@Lazy EncryptionUtil encryptionUtil) {
        this.encryptionUtil = encryptionUtil;
    }

    /** Encrypts the value before storing it in the database column. */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        return encryptionUtil.encrypt(attribute);
    }

    /**
     * Decrypts the value when reading from the database. Falls back to raw value on
     * error (for legacy plain text data).
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        try {
            return encryptionUtil.decrypt(dbData);
        } catch (Exception e) {
            // Fallback for existing plain text data or decryption errors
            return dbData;
        }
    }
}

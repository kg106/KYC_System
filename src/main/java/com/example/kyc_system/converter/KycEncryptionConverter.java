package com.example.kyc_system.converter;

import com.example.kyc_system.util.EncryptionUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Converter
public class KycEncryptionConverter implements AttributeConverter<String, String> {

    private final EncryptionUtil encryptionUtil;

    public KycEncryptionConverter(@Lazy EncryptionUtil encryptionUtil) {
        this.encryptionUtil = encryptionUtil;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        return encryptionUtil.encrypt(attribute);
    }

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

package com.example.kyc_system.util;

import com.example.kyc_system.config.KycProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class KycFileValidator {

    private final KycProperties kycProperties;

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Basic size validation (placeholder for more complex logic if needed)
        // Note: Actual size check usually happens at the controller level or via
        // properties
        // but this is where custom logic would go.

        String contentType = file.getContentType();
        if (contentType == null || !kycProperties.getFile().getAllowedTypes().contains(contentType)) {
            throw new IllegalArgumentException("Invalid file type: " + contentType);
        }
    }
}

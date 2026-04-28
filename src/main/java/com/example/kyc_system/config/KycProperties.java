package com.example.kyc_system.config;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Maps custom properties from application.properties under the "kyc" prefix.
 * Example: kyc.storage.base-path, kyc.file.max-size, kyc.file.allowed-types
 */
@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "kyc")
public class KycProperties {

    /**
     * Storage configuration settings.
     */
    private Storage storage = new Storage();

    /**
     * File upload configuration settings.
     */
    private File file = new File();

    /**
     * Inner class for storage related properties (e.g., base path for file storage).
     */
    @Setter
    @Getter
    public static class Storage {
        /**
         * Base directory path where KYC documents are stored.
         */
        private String basePath;

    }

    /**
     * Inner class for file upload constraints (max size, allowed MIME types).
     */
    @Setter
    @Getter
    public static class File {
        /**
         * Maximum allowed file size (e.g., "5MB").
         */
        private String maxSize;

        /**
         * List of allowed MIME types for document uploads.
         */
        private List<String> allowedTypes;

    }
}

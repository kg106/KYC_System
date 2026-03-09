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

    private Storage storage = new Storage();
    private File file = new File();

    /** Storage configuration (e.g., base path for file storage). */
    @Setter
    @Getter
    public static class Storage {
        private String basePath;

    }

    /** File upload constraints (max size, allowed MIME types). */
    @Setter
    @Getter
    public static class File {
        private String maxSize;
        private List<String> allowedTypes;

    }
}

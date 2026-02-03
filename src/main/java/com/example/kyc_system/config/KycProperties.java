package com.example.kyc_system.config;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "kyc")
public class KycProperties {

    private Storage storage = new Storage();
    private File file = new File();

    @Setter
    @Getter
    public static class Storage {
        private String basePath;

    }

    @Setter
    @Getter
    public static class File {
        private String maxSize;
        private List<String> allowedTypes;

    }
}

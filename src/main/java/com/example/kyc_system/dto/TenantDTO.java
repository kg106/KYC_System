package com.example.kyc_system.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDTO {
    private Long id;
    private String tenantId;
    private String name;
    private String email;
    private String plan;
    private Boolean isActive;
    private Integer maxDailyAttempts;
    private String allowedDocumentTypes;
    private String apiKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
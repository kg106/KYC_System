package com.example.kyc_system.service.impl;

import com.example.kyc_system.context.TenantContext;
import com.example.kyc_system.entity.AuditLog;
import com.example.kyc_system.repository.AuditLogRepository;
import com.example.kyc_system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of AuditLogService.
 * Persists audit logs asynchronously to avoid blocking the main request thread.
 * Includes data masking for sensitive fields (PII).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /** Keys that should be masked before persisting to the audit log. */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "token", "secret", "authorization",
            "aadhaar", "pan", "passport", "documentnumber", "dob");

    /**
     * Logs an action with a human-readable string.
     * Runs asynchronously.
     */
    @Override
    @Async
    public void logAction(String action, String entityType, Long entityId, String details, String performedBy) {
        try {
            Map<String, Object> detailsMap = new HashMap<>();
            detailsMap.put("message", details);
            logActionInternal(action, entityType, entityId, detailsMap, performedBy);
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
    }

    /**
     * Logs an action with a map of metadata.
     * Runs asynchronously.
     */
    @Override
    @Async
    public void logAction(String action, String entityType, Long entityId, Map<String, Object> detailsMap,
            String performedBy) {
        try {
            logActionInternal(action, entityType, entityId, detailsMap, performedBy);
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
    }

    /**
     * Internal helper to build and save the AuditLog entity.
     * Performs data masking before save.
     */
    private void logActionInternal(String action, String entityType, Long entityId, Map<String, Object> detailsMap,
            String performedBy) {
        String tenantIdStr = TenantContext.getTenant() != null ? TenantContext.getTenant() : "00000000-0000-0000-0000-000000000000";
        java.util.UUID tenantId = java.util.UUID.fromString(tenantIdStr);
        Map<String, Object> maskedDetails = maskSensitiveData(detailsMap);

        AuditLog auditLog = AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .newValue(maskedDetails)
                .performedBy(performedBy)
                .tenantId(tenantId)
                .build();

        log.trace("Saving audit log: action={}, entityType={}, entityId={}", action, entityType, entityId);
        auditLogRepository.save(auditLog);
    }

    /**
     * Recursively masks sensitive fields in the details map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> maskSensitiveData(Map<String, Object> data) {
        if (data == null)
            return null;
        Map<String, Object> maskedData = new HashMap<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (isSensitiveKey(key)) {
                maskedData.put(key, "****");
            } else if (value instanceof Map) {
                maskedData.put(key, maskSensitiveData((Map<String, Object>) value));
            } else {
                maskedData.put(key, value);
            }
        }
        return maskedData;
    }

    /** Checks if a key name suggests it contains PII. */
    private boolean isSensitiveKey(String key) {
        if (key == null)
            return false;
        String lowerKey = key.toLowerCase();
        for (String sensitive : SENSITIVE_KEYS) {
            if (lowerKey.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }
}

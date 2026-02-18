package com.example.kyc_system.service;

import java.util.Map;

public interface AuditLogService {
    void logAction(String action, String entityType, Long entityId, String details, String performedBy);

    void logAction(String action, String entityType, Long entityId, Map<String, Object> detailsMap, String performedBy);
}

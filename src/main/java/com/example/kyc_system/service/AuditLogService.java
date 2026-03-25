package com.example.kyc_system.service;

import java.util.Map;

/**
 * Service interface for platform-wide audit logging.
 * Captures significant events for security and compliance audits.
 */
public interface AuditLogService {
    /**
     * Logs a simple action with a string description.
     *
     * @param action the type of action (e.g., "LOGIN", "SUBMIT")
     * @param entityType the type of entity affected (e.g., "User", "KycRequest")
     * @param entityId ID of the entity
     * @param details human-readable explanation
     * @param performedBy username/identifier of the actor
     */
    void logAction(String action, String entityType, Long entityId, String details, String performedBy);

    /**
     * Logs a complex action with structured metadata.
     *
     * @param action the type of action
     * @param entityType the type of entity
     * @param entityId ID of the entity
     * @param detailsMap key-value pairs of relevant metadata
     * @param performedBy identifier of the actor
     */
    void logAction(String action, String entityType, Long entityId, Map<String, Object> detailsMap, String performedBy);
}

package com.example.kyc_system.repository;

import com.example.kyc_system.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for AuditLog entity.
 * Provides access to system-wide audit trails.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Finds audit logs for a specific entity type and ID.
     *
     * @param entityType the type of entity (e.g., "User", "KycRequest")
     * @param entityId the primary key of the entity
     * @return list of matching audit logs
     */
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);

    /**
     * Finds audit logs for actions performed by a specific user (email).
     *
     * @param performedBy the email of the performer
     * @return list of matching audit logs
     */
    List<AuditLog> findByPerformedBy(String performedBy);

    /**
     * Finds audit logs associated with a specific logical request ID.
     *
     * @param requestId the unique request identifier
     * @return list of matching audit logs
     */
    List<AuditLog> findByRequestId(String requestId);
}

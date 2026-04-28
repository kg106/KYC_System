package com.example.kyc_system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity representing an audit log entry for tracking changes to other entities.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    /**
     * Unique identifier for the audit log entry.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type of the entity that was changed (e.g., "User", "KycRequest").
     */
    private String entityType;

    /**
     * ID of the entity that was changed.
     */
    private Long entityId;

    /**
     * The action performed (e.g., "CREATE", "UPDATE", "DELETE").
     */
    private String action;

    /**
     * The user ID or system component that performed the action.
     */
    private String performedBy;

    /**
     * The previous state of the entity in JSON format.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> oldValue;

    /**
     * The new state of the entity in JSON format.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> newValue;

    /**
     * IP address from which the action was initiated.
     */
    private String ipAddress;

    /**
     * User agent string from the request.
     */
    private String userAgent;

    /**
     * Correlation ID for tracing the request across services.
     */
    private String correlationId;

    /**
     * ID of the tenant where the action occurred.
     */
    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    /**
     * Unique request ID.
     */
    private String requestId;

    /**
     * Timestamp when the audit log entry was created.
     */
    @CreationTimestamp
    private LocalDateTime createdAt;
}

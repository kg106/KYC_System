package com.example.kyc_system.repository;

import com.example.kyc_system.entity.KycRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for KycRequest entity.
 * Handles extensive query logic for request statuses, attempt limits, and reporting.
 */
@Repository
public interface KycRequestRepository extends JpaRepository<KycRequest, Long>, JpaSpecificationExecutor<KycRequest> {

    /**
     * Finds all KYC requests for a given user.
     *
     * @param userId the user's ID
     * @return a list of KYC requests
     */
    List<KycRequest> findByUserId(UUID userId);

    /**
     * Finds all KYC requests for a given user within a specific tenant.
     *
     * @param userId the user's ID
     * @param tenantId the unique identifier for the tenant
     * @return a list of KYC requests
     */
    List<KycRequest> findByUserIdAndTenantId(UUID userId, UUID tenantId);

    /**
     * Counts the total number of KYC requests in a specific tenant.
     *
     * @param tenantId the unique identifier for the tenant
     * @return total request count
     */
    long countByTenantId(UUID tenantId);

    /**
     * Counts KYC requests in a specific tenant filtered by their status.
     *
     * @param tenantId the unique identifier for the tenant
     * @param status the status to filter by (e.g., "PENDING")
     * @return matching request count
     */
    long countByTenantIdAndStatus(UUID tenantId, String status);

    /**
     * Finds KYC requests created within a specific time range.
     *
     * @param from start of the time range (inclusive)
     * @param to end of the time range (exclusive)
     * @return list of matching requests
     */
    List<KycRequest> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    /**
     * Finds KYC requests in a time range and eager-loads associated User and Tenant entities.
     * Optimized for report generation to avoid N+1 query issues.
     *
     * @param from start of the time range (inclusive)
     * @param to end of the time range (exclusive)
     * @return list of matching requests with fetched associations
     */
    @Query("SELECT kr FROM KycRequest kr WHERE kr.createdAt BETWEEN :from AND :to")
    List<KycRequest> findByCreatedAtBetweenWithUserAndTenant(@Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Finds KYC requests for a specific tenant in a time range and eager-loads associations.
     *
     * @param tenantId the tenant ID
     * @param from start of range
     * @param to end of range
     * @return list of matching requests
     */
    @Query("SELECT kr FROM KycRequest kr WHERE kr.tenantId = :tenantId AND kr.createdAt BETWEEN :from AND :to")
    List<KycRequest> findByCreatedAtBetweenWithUserAndTenant(@Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Retrives the latest KYC request for a user within a tenant.
     *
     * @param userId the user's ID
     * @param tenantId the unique identifier for the tenant
     * @return an Optional containing the latest request
     */
    Optional<KycRequest> findTopByUserIdAndTenantIdOrderByCreatedAtDesc(
            UUID userId, UUID tenantId);

    /**
     * Retrieves the latest KYC request for a user, specific document type, and tenant.
     *
     * @param userId the user's ID
     * @param documentType the type of document (e.g., "AADHAAR")
     * @param tenantId the unique identifier for the tenant
     * @return an Optional containing the latest matching request
     */
    Optional<KycRequest> findTopByUserIdAndDocumentTypeAndTenantIdOrderByCreatedAtDesc(
            UUID userId, String documentType, UUID tenantId);

    /**
     * Retrieves the latest KYC request for a user globally, ordered by attempt number.
     *
     * @param userId the user's ID
     * @return an Optional containing the highest attempt number request
     */
    Optional<KycRequest> findTopByUserIdOrderByAttemptNumberDesc(UUID userId);

    /**
     * Retrieves the latest KYC request for a user globally.
     *
     * @param userId the user's ID
     * @return an Optional containing the latest request
     */
    Optional<KycRequest> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Retrieves the latest KYC request for a user and document type globally.
     *
     * @param userId the user's ID
     * @param documentType the document type
     * @return an Optional containing the latest matching request
     */
    Optional<KycRequest> findTopByUserIdAndDocumentTypeOrderByCreatedAtDesc(UUID userId, String documentType);

    /**
     * Sums the attempt numbers for a user since or at a specific time today.
     * Used for rate-limiting KYC submissions.
     *
     * @param userId the user's ID
     * @param startOfDay the timestamp for the start of the day
     * @return total attempts made today
     */
    @Query("SELECT COALESCE(SUM(k.attemptNumber), 0) FROM KycRequest k WHERE k.userId = :userId AND k.submittedAt >= :startOfDay")
    long sumAttemptNumberByUserIdAndSubmittedAtGreaterThanEqual(@Param("userId") UUID userId,
            @Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Sums the attempt numbers for a user within a tenant since or at a specific time today.
     * Used for tenant-scoped rate-limiting.
     *
     * @param userId the user's ID
     * @param tenantId the tenant ID
     * @param startOfDay the start of the day timestamp
     * @return total attempts made today
     */
    @Query("""
                    SELECT COALESCE(SUM(k.attemptNumber), 0)
                    FROM KycRequest k
                    WHERE k.userId = :userId
                    AND k.tenantId = :tenantId
                    AND k.submittedAt >= :startOfDay
            """)
    long sumAttemptNumberByUserIdAndTenantIdAndSubmittedAtAfter(
            @Param("userId") UUID userId,
            @Param("tenantId") UUID tenantId,
            @Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Counts the number of KYC requests submitted by a user today.
     *
     * @param userId the user's ID
     * @param startOfDay the start of the day timestamp
     * @return count of submissions today
     */
    long countByUserIdAndSubmittedAtGreaterThanEqual(UUID userId, LocalDateTime startOfDay);

    /**
     * Finds all requests currently in a specific status.
     *
     * @param status the status string (e.g., "SUBMITTED")
     * @return list of matching requests
     */
    List<KycRequest> findByStatus(String status);

    /**
     * Updates the status and updatedAt timestamp of a specific KYC request.
     *
     * @param kycRequestId the ID of the request to update
     * @param status the new status
     * @return number of rows affected
     */
    @Modifying
    @Query("""
            UPDATE KycRequest k
            SET k.status = :status,
            k.updatedAt = CURRENT_TIMESTAMP
            WHERE k.id = :kycRequestId
            """)
    int updateStatus(@Param("kycRequestId") Long kycRequestId, @Param("status") String status);

    /**
     * Atomically updates a request's status only if it matches the expected old status.
     *
     * @param id the ID of the request
     * @param newStatus the target status
     * @param oldStatus the required current status
     * @return number of rows affected
     */
    @Modifying
    @Query("UPDATE KycRequest k SET k.status = :newStatus, k.updatedAt = CURRENT_TIMESTAMP WHERE k.id = :id AND k.status = :oldStatus")
    int updateStatusIfPending(@Param("id") Long id, @Param("newStatus") String newStatus,
            @Param("oldStatus") String oldStatus);

    /**
     * Returns a breakdown of request counts grouped by status for a global time range.
     * Used for management dashboards and global reporting.
     *
     * @param from start of time range
     * @param to end of time range
     * @return list of status-count pairs
     */
    @Query("""
            SELECT k.status, COUNT(k) FROM KycRequest k
            WHERE k.submittedAt >= :from AND k.submittedAt < :to
            GROUP BY k.status
            """)
    List<Object[]> countByStatusBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Returns a breakdown of request counts grouped by status for a specific tenant and time range.
     * Used for tenant-specific dashboards.
     *
     * @param tenantId the tenant ID
     * @param from start of time range
     * @param to end of time range
     * @return list of status-count pairs
     */
    @Query("""
                SELECT k.status, COUNT(k) FROM KycRequest k
                WHERE k.tenantId = :tenantId
                  AND k.submittedAt >= :from
                  AND k.submittedAt < :to
                GROUP BY k.status
            """)
    List<Object[]> countByStatusBetween(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Returns a breakdown of request counts grouped by document type for a global time range.
     *
     * @param from start of time range
     * @param to end of time range
     * @return list of type-count pairs
     */
    @Query("""
                SELECT k.documentType, COUNT(k) FROM KycRequest k
                WHERE k.submittedAt >= :from AND k.submittedAt < :to
                GROUP BY k.documentType
            """)
    List<Object[]> countByDocumentTypeBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Returns a breakdown of request counts grouped by document type for a specific tenant and time range.
     *
     * @param tenantId the tenant ID
     * @param from start of time range
     * @param to end of time range
     * @return list of type-count pairs
     */
    @Query("""
                SELECT k.documentType, COUNT(k) FROM KycRequest k
                WHERE k.tenantId = :tenantId
                  AND k.submittedAt >= :from
                  AND k.submittedAt < :to
                GROUP BY k.documentType
            """)
    List<Object[]> countByDocumentTypeBetween(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

}

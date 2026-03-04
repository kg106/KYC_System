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

@Repository
public interface KycRequestRepository extends JpaRepository<KycRequest, Long>, JpaSpecificationExecutor<KycRequest> {

        List<KycRequest> findByUserId(Long userId);

        List<KycRequest> findByUserIdAndTenantId(Long userId, String tenantId);

        long countByTenantId(String tenantId);

        long countByTenantIdAndStatus(String tenantId, String status);

        Optional<KycRequest> findTopByUserIdAndTenantIdOrderByCreatedAtDesc(
                        Long userId, String tenantId);

        Optional<KycRequest> findTopByUserIdAndDocumentTypeAndTenantIdOrderByCreatedAtDesc(
                        Long userId, String documentType, String tenantId);

        Optional<KycRequest> findTopByUserIdOrderByAttemptNumberDesc(Long userId);

        Optional<KycRequest> findTopByUserIdOrderByCreatedAtDesc(Long userId);

        Optional<KycRequest> findTopByUserIdAndDocumentTypeOrderByCreatedAtDesc(Long userId, String documentType);

        @Query("SELECT COALESCE(SUM(k.attemptNumber), 0) FROM KycRequest k WHERE k.user.id = :userId AND k.submittedAt >= :startOfDay")
        long sumAttemptNumberByUserIdAndSubmittedAtGreaterThanEqual(@Param("userId") Long userId,
                        @Param("startOfDay") java.time.LocalDateTime startOfDay);

        @Query("""
                                SELECT COALESCE(SUM(k.attemptNumber), 0)
                                FROM KycRequest k
                                WHERE k.user.id = :userId
                                AND k.tenantId = :tenantId
                                AND k.submittedAt >= :startOfDay
                        """)
        long sumAttemptNumberByUserIdAndTenantIdAndSubmittedAtAfter(
                        @Param("userId") Long userId,
                        @Param("tenantId") String tenantId,
                        @Param("startOfDay") LocalDateTime startOfDay);

        long countByUserIdAndSubmittedAtGreaterThanEqual(Long userId, java.time.LocalDateTime startOfDay);

        List<KycRequest> findByStatus(String status);

        @Modifying
        @Query("""
                        UPDATE KycRequest k
                        SET k.status = :status,
                        k.updatedAt = CURRENT_TIMESTAMP
                        WHERE k.id = :kycRequestId
                        """)
        void updateStatus(@Param("kycRequestId") Long kycRequestId, @Param("status") String status);

        @Modifying
        @Query("UPDATE KycRequest k SET k.status = :newStatus, k.updatedAt = CURRENT_TIMESTAMP WHERE k.id = :id AND k.status = :oldStatus")
        int updateStatusIfPending(@Param("id") Long id, @Param("newStatus") String newStatus,
                        @Param("oldStatus") String oldStatus);

        @Query("""
                        SELECT k.status, COUNT(k) FROM KycRequest k
                        WHERE k.submittedAt >= :from AND k.submittedAt < :to
                        GROUP BY k.status
                        """)
        List<Object[]> countByStatusBetween(
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

        @Query("""
                            SELECT k.status, COUNT(k) FROM KycRequest k
                            WHERE k.tenantId = :tenantId
                              AND k.submittedAt >= :from
                              AND k.submittedAt < :to
                            GROUP BY k.status
                        """)
        List<Object[]> countByStatusBetween(
                        @Param("tenantId") String tenantId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

        @Query("""
                            SELECT k.documentType, COUNT(k) FROM KycRequest k
                            WHERE k.submittedAt >= :from AND k.submittedAt < :to
                            GROUP BY k.documentType
                        """)
        List<Object[]> countByDocumentTypeBetween(
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

        @Query("""
                            SELECT k.documentType, COUNT(k) FROM KycRequest k
                            WHERE k.tenantId = :tenantId
                              AND k.submittedAt >= :from
                              AND k.submittedAt < :to
                            GROUP BY k.documentType
                        """)
        List<Object[]> countByDocumentTypeBetween(
                        @Param("tenantId") String tenantId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

}

package com.example.kyc_system.repository;

import com.example.kyc_system.entity.KycRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KycRequestRepository extends JpaRepository<KycRequest, Long>, JpaSpecificationExecutor<KycRequest> {

    List<KycRequest> findByUserId(Long userId);

    Optional<KycRequest> findTopByUserIdOrderByAttemptNumberDesc(Long userId);

    Optional<KycRequest> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<KycRequest> findTopByUserIdAndDocumentTypeOrderByCreatedAtDesc(Long userId, String documentType);

    @Query("SELECT COALESCE(SUM(k.attemptNumber), 0) FROM KycRequest k WHERE k.user.id = :userId AND k.submittedAt >= :startOfDay")
    long sumAttemptNumberByUserIdAndSubmittedAtGreaterThanEqual(@Param("userId") Long userId,
            @Param("startOfDay") java.time.LocalDateTime startOfDay);

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
}

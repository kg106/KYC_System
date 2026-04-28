package com.example.kyc_system.repository;

import com.example.kyc_system.entity.KycVerificationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for KycVerificationResult entity.
 * Stores the final outcome of the KYC verification process.
 */
@Repository
public interface KycVerificationResultRepository extends JpaRepository<KycVerificationResult, Long> {

    /**
     * Finds the verification result associated with a specific KYC request.
     *
     * @param kycRequestId the ID of the KYC request
     * @return an Optional containing the result, or empty if not found
     */
    Optional<KycVerificationResult> findByKycRequestId(Long kycRequestId);

    /**
     * Lists all verification results with a specific final status.
     *
     * @param finalStatus the status string (e.g., "MATCHED")
     * @return list of matching results
     */
    List<KycVerificationResult> findByFinalStatus(String finalStatus);
}

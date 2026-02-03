package com.example.kyc_system.repository;

import com.example.kyc_system.entity.KycVerificationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KycVerificationResultRepository extends JpaRepository<KycVerificationResult, Long> {

    Optional<KycVerificationResult> findByKycRequestId(Long kycRequestId);

    List<KycVerificationResult> findByFinalStatus(String finalStatus);
}


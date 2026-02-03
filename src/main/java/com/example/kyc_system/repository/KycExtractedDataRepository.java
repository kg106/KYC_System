package com.example.kyc_system.repository;

import com.example.kyc_system.entity.KycExtractedData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KycExtractedDataRepository extends JpaRepository<KycExtractedData, Long> {

    Optional<KycExtractedData> findByKycDocumentId(Long kycDocumentId);

    @Query("""
        SELECT e FROM KycExtractedData e
        WHERE e.extractedName IS NOT NULL
          AND e.extractedDob IS NOT NULL
          AND e.extractedDocumentNumber IS NOT NULL
    """)
    List<KycExtractedData> findCompleteExtractions();
}


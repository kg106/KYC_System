package com.example.kyc_system.repository;

import com.example.kyc_system.entity.KycExtractedData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for KycExtractedData entity.
 * Stores OCR/AI results extracted from uploaded documents.
 */
@Repository
public interface KycExtractedDataRepository extends JpaRepository<KycExtractedData, Long> {

  /**
   * Finds extracted data for a specific document.
   *
   * @param kycDocumentId the ID of the document
   * @return an Optional containing the extracted data
   */
  Optional<KycExtractedData> findByKycDocumentId(Long kycDocumentId);

  /**
   * Retrieves all extractions that have a complete set of core fields (name, DOB, document number).
   *
   * @return a list of fully extracted data entities
   */
  @Query("""
          SELECT e FROM KycExtractedData e
          WHERE e.extractedName IS NOT NULL
            AND e.extractedDob IS NOT NULL
            AND e.extractedDocumentNumber IS NOT NULL
      """)
  List<KycExtractedData> findCompleteExtractions();
}

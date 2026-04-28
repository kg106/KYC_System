package com.example.kyc_system.repository;

import com.example.kyc_system.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for KycDocument entity.
 * Manages document metadata and provides validation queries.
 */
@Repository
public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {

  /**
   * Finds all documents associated with a specific KYC request.
   *
   * @param kycRequestId the ID of the KYC request
   * @return list of matching documents
   */
  List<KycDocument> findByKycRequest_Id(Long kycRequestId);

  /**
   * Checks if a document of a specific type has already been uploaded for a request.
   *
   * @param kycRequestId the ID of the KYC request
   * @param documentType the type of document (e.g., "AADHAAR_FRONT")
   * @return true if it exists, false otherwise
   */
  boolean existsByKycRequest_IdAndDocumentType(Long kycRequestId, String documentType);

  /**
   * Filters documents for a KYC request that meet specific size and MIME type constraints.
   *
   * @param kycRequestId the ID of the KYC request
   * @param maxSize maximum allowed file size in bytes
   * @param allowedTypes list of permitted MIME types
   * @return list of valid documents
   */
  @Query("""
          SELECT d FROM KycDocument d
          WHERE d.kycRequest.id = :kycRequestId
            AND d.fileSize <= :maxSize
            AND d.mimeType IN :allowedTypes
      """)
  List<KycDocument> findValidDocuments(@Param("kycRequestId") Long kycRequestId,
      @Param("maxSize") Long maxSize,
      @Param("allowedTypes") List<String> allowedTypes);

  /**
   * Checks for duplicate active requests for the same user, document type, and document number.
   * Used to prevent multiple simultaneous processing of the same physical document.
   *
   * @param userId the user's ID
   * @param documentType the type of document
   * @param documentNumber the unique number on the document (e.g., Aadhaar number)
   * @param status the request status to check against
   * @return true if a matching active request exists
   */
  boolean existsByKycRequest_User_IdAndDocumentTypeAndDocumentNumberAndKycRequest_Status(
      Long userId, String documentType, String documentNumber, String status);
}

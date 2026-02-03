package com.example.kyc_system.repository;

import com.example.kyc_system.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {

    List<KycDocument> findByKycRequest_Id(Long kycRequestId);

    boolean existsByKycRequest_IdAndDocumentType(Long kycRequestId, String documentType);

    @Query("""
        SELECT d FROM KycDocument d
        WHERE d.kycRequest.id = :kycRequestId
          AND d.fileSize <= :maxSize
          AND d.mimeType IN :allowedTypes
    """)
    List<KycDocument> findValidDocuments(@Param("kycRequestId") Long kycRequestId,
                                         @Param("maxSize") Long maxSize,
                                         @Param("allowedTypes") List<String> allowedTypes);
}



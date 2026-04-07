package com.example.kyc_system.repository.specification;

import com.example.kyc_system.dto.KycRequestSearchDTO;
import com.example.kyc_system.entity.KycRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.util.UUID;

import java.util.ArrayList;
import java.util.List;

public class KycRequestSpecification {

    public static Specification<KycRequest> buildSpecification(KycRequestSearchDTO searchDTO, String tenantId,
            boolean isSuperAdmin) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Multi-tenancy scoping
            if (!isSuperAdmin && tenantId != null && !tenantId.isBlank()) {
                predicates.add(cb.equal(root.get("tenantId"), UUID.fromString(tenantId)));
            }

            if (searchDTO.getUserId() != null) {
                predicates.add(cb.equal(root.get("userId"), UUID.fromString(searchDTO.getUserId())));
            }

            if (searchDTO.getUserName() != null && !searchDTO.getUserName().isEmpty()) {
                // userName search is disabled in KYC service since User was removed.
                // It should be fetched/filtered at the API Gateway or Auth Service layer.
            }

            if (searchDTO.getStatus() != null && !searchDTO.getStatus().isEmpty()) {
                predicates.add(cb.equal(root.get("status"), searchDTO.getStatus()));
            }

            if (searchDTO.getDocumentType() != null && !searchDTO.getDocumentType().isEmpty()) {
                predicates.add(cb.equal(root.get("documentType"), searchDTO.getDocumentType()));
            }

            if (searchDTO.getDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), searchDTO.getDateFrom()));
            }

            if (searchDTO.getDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("submittedAt"), searchDTO.getDateTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

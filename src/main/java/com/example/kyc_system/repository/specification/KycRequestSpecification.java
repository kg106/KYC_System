package com.example.kyc_system.repository.specification;

import com.example.kyc_system.dto.KycRequestSearchDTO;
import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.entity.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class KycRequestSpecification {

    public static Specification<KycRequest> buildSpecification(KycRequestSearchDTO searchDTO) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (searchDTO.getUserId() != null) {
                predicates.add(cb.equal(root.get("user").get("id"), searchDTO.getUserId()));
            }

            if (searchDTO.getUserName() != null && !searchDTO.getUserName().isEmpty()) {
                Join<KycRequest, User> userJoin = root.join("user");
                predicates.add(
                        cb.like(cb.lower(userJoin.get("name")), "%" + searchDTO.getUserName().toLowerCase() + "%"));
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

package com.example.kyc_system.repository.specification;

import com.example.kyc_system.dto.UserSearchDTO;
import com.example.kyc_system.entity.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UserSpecification {

    public static Specification<User> buildSpecification(UserSearchDTO searchDTO) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (searchDTO.getName() != null && !searchDTO.getName().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + searchDTO.getName().toLowerCase() + "%"));
            }

            if (searchDTO.getEmail() != null && !searchDTO.getEmail().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("email")), "%" + searchDTO.getEmail().toLowerCase() + "%"));
            }

            if (searchDTO.getMobileNumber() != null && !searchDTO.getMobileNumber().isEmpty()) {
                predicates.add(cb.like(root.get("mobileNumber"), "%" + searchDTO.getMobileNumber() + "%"));
            }

            if (searchDTO.getIsActive() != null) {
                predicates.add(cb.equal(root.get("isActive"), searchDTO.getIsActive()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

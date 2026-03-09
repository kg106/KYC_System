package com.example.kyc_system.repository;

import com.example.kyc_system.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for role definitions (ROLE_USER, ROLE_ADMIN, ROLE_TENANT_ADMIN,
 * ROLE_SUPER_ADMIN).
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
}

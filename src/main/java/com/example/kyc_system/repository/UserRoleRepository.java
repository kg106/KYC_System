package com.example.kyc_system.repository;

import com.example.kyc_system.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for user-role assignments (junction table between users and roles).
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    /**
     * Finds all role assignments for a specific user.
     *
     * @param userId the user's ID
     * @return a list of user-role mapping entities
     */
    List<UserRole> findByUserId(Long userId);
}

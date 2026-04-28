package com.example.kyc_system.repository;

import com.example.kyc_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User entity.
 * Supports multi-tenancy and complex matching queries.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

  /**
   * Finds a user by their primary ID.
   *
   * @param id the user's unique ID
   * @return an Optional containing the found user, or empty if not found
   */
  Optional<User> findById(Long id);

  /**
   * Finds a user by email within a specific tenant.
   *
   * @param email the user's email address
   * @param tenantId the unique identifier for the tenant
   * @return an Optional containing the found user, or empty if not found
   */
  Optional<User> findByEmailAndTenantId(String email, String tenantId);

  /**
   * Finds a user by ID within a specific tenant.
   *
   * @param id the user's unique ID
   * @param tenantId the unique identifier for the tenant
   * @return an Optional containing the found user, or empty if not found
   */
  Optional<User> findByIdAndTenantId(Long id, String tenantId);

  /**
   * Lists all users belonging to a specific tenant.
   *
   * @param tenantId the unique identifier for the tenant
   * @return a list of matching users
   */
  List<User> findByTenantId(String tenantId);

  /**
   * Counts the total number of users in a specific tenant.
   *
   * @param tenantId the unique identifier for the tenant
   * @return the user count
   */
  long countByTenantId(String tenantId);

  /**
   * Checks if a user with the given email exists within a specific tenant.
   *
   * @param email the email to search for
   * @param tenantId the unique identifier for the tenant
   * @return true if the user exists, false otherwise
   */
  boolean existsByEmailAndTenantId(String email, String tenantId);

  /**
   * Performs fuzzy/exact matching for KYC verification.
   * Searches for a user by name (case-insensitive) and date of birth within a tenant.
   *
   * @param name the user's full name to match
   * @param dob the user's date of birth
   * @param tenantId the unique identifier for the tenant
   * @return an Optional containing the matched user, or empty if no match found
   */
  @Query("""
          SELECT u FROM User u
          WHERE LOWER(u.name) = LOWER(:name)
            AND u.dob = :dob
            AND u.tenantId = :tenantId
      """)
  Optional<User> matchUserData(@Param("name") String name,
      @Param("dob") LocalDate dob,
      @Param("tenantId") String tenantId);

  /**
   * Finds a user by email (global search).
   *
   * @param email the user's email address
   * @return an Optional containing the found user, or empty if not found
   */
  Optional<User> findByEmail(String email);

  /**
   * Counts new user registrations within a tenant for a specific time range.
   *
   * @param tenantId the unique identifier for the tenant
   * @param from start of the time range (inclusive)
   * @param to end of the time range (exclusive)
   * @return count of new users
   */
  @Query("""
          SELECT COUNT(u) FROM User u
          WHERE u.tenantId = :tenantId
            AND u.createdAt >= :from
            AND u.createdAt < :to
      """)
  long countNewUsersBetween(@Param("tenantId") String tenantId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  /**
   * Counts new user registrations globally for a specific time range.
   *
   * @param from start of the time range (inclusive)
   * @param to end of the time range (exclusive)
   * @return count of new users
   */
  @Query("""
          SELECT COUNT(u) FROM User u
          WHERE u.createdAt >= :from
            AND u.createdAt < :to
      """)
  long countNewUsersBetween(@Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

}

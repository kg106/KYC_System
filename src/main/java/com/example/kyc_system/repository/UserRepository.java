package com.example.kyc_system.repository;

import com.example.kyc_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

  Optional<User> findById(Long id);

  @Query("""
          SELECT u FROM User u
          WHERE LOWER(u.name) = LOWER(:name)
            AND u.dob = :dob
      """)
  Optional<User> matchUserData(@Param("name") String name, @Param("dob") LocalDate dob);

  Optional<User> findByEmail(String email);
}

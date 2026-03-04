package com.example.kyc_system.config;

import com.example.kyc_system.entity.*;
import com.example.kyc_system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Step 1 — Create all roles
        createRoleIfNotFound("ROLE_SUPER_ADMIN");
        createRoleIfNotFound("ROLE_TENANT_ADMIN");
        createRoleIfNotFound("ROLE_USER");

        // Step 2 — Create system tenant for superadmin
        Tenant systemTenant = tenantRepository.findByTenantId("system")
                .orElseGet(() -> tenantRepository.save(
                        Tenant.builder()
                                .tenantId("system")
                                .name("System")
                                .email("system@kyc.com")
                                .isActive(true)
                                .build()));

        // Step 3 — Create superadmin user under system tenant
        if (userRepository.findByEmailAndTenantId(
                "superadmin@kyc.com", "system").isEmpty()) {

            User superAdmin = User.builder()
                    .name("Super Admin")
                    .email("superadmin@kyc.com")
                    .mobileNumber("9999999999")
                    .passwordHash(passwordEncoder.encode("SuperAdmin@123"))
                    .tenantId("system")
                    .isActive(true)
                    .dob(LocalDate.of(1990, 1, 1))
                    .build();

            User saved = userRepository.save(superAdmin);

            roleRepository.findByName("ROLE_SUPER_ADMIN")
                    .ifPresent(role -> userRoleRepository.save(
                            UserRole.builder()
                                    .user(saved)
                                    .role(role)
                                    .build()));

            log.info("Superadmin created: superadmin@kyc.com / SuperAdmin@123");
        }

        // Step 4 — Ensure default tenant exists
        tenantRepository.findByTenantId("default")
                .orElseGet(() -> tenantRepository.save(
                        Tenant.builder()
                                .tenantId("default")
                                .name("Default Tenant")
                                .email("admin@kyc.com")
                                .isActive(true)
                                .build()));

        // Step 5 — Create old admin under default tenant
        if (userRepository.findByEmailAndTenantId(
                "admin@kyc.com", "default").isEmpty()) {

            User admin = User.builder()
                    .name("Default Admin")
                    .email("admin@kyc.com")
                    .mobileNumber("0000000000")
                    .passwordHash(passwordEncoder.encode("Password@123"))
                    .tenantId("default")
                    .isActive(true)
                    .dob(LocalDate.of(1990, 1, 1))
                    .build();

            User saved = userRepository.save(admin);

            roleRepository.findByName("ROLE_TENANT_ADMIN")
                    .ifPresent(role -> userRoleRepository.save(
                            UserRole.builder()
                                    .user(saved)
                                    .role(role)
                                    .build()));

            log.info("Default tenant admin created: admin@kyc.com / Password@123");
        }

        // Step 6 — DB constraints
        initUniqueIndex();
    }

    private void initUniqueIndex() {
        try {
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS unique_active_kyc " +
                            "ON kyc_requests(user_id, document_type) " +
                            "WHERE status IN ('PENDING', 'SUBMITTED', 'PROCESSING')");
        } catch (Exception e) {
            log.warn("Could not create unique index: {}", e.getMessage());
        }
    }

    private Role createRoleIfNotFound(String name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name(name).build()));
    }
}
package com.example.kyc_system.config;

import com.example.kyc_system.entity.Role;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.entity.UserRole;
import com.example.kyc_system.repository.RoleRepository;
import com.example.kyc_system.repository.UserRepository;
import com.example.kyc_system.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Initialize Roles
        Role adminRole = createRoleIfNotFound("ROLE_ADMIN");
        Role userRole = createRoleIfNotFound("ROLE_USER");

        // Initialize Admin User
        if (userRepository.findByEmail("admin@kyc.com").isEmpty()) {
            User admin = User.builder()
                    .name("System Admin")
                    .email("admin@kyc.com")
                    .mobileNumber("0000000000")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .dob(LocalDate.of(1990, 1, 1))
                    .isActive(true)
                    .build();
            User savedAdmin = userRepository.save(admin);

            UserRole adminUserRole = UserRole.builder()
                    .user(savedAdmin)
                    .role(adminRole)
                    .build();
            userRoleRepository.save(adminUserRole);
        }
    }

    private Role createRoleIfNotFound(String name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(Role.builder().name(name).build()));
    }
}

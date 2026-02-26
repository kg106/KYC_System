package com.example.kyc_system.integration;

import com.example.kyc_system.config.TestContainersConfig;
import com.example.kyc_system.entity.Role;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.entity.UserRole;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.repository.RoleRepository;
import com.example.kyc_system.repository.UserRepository;
import com.example.kyc_system.repository.UserRoleRepository;
import com.example.kyc_system.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
// import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestContainersConfig.class)
@ActiveProfiles("test") // picks up application-test.properties
@Transactional // rolls back DB after every test
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected UserRoleRepository userRoleRepository;

    @Autowired
    protected KycRequestRepository kycRequestRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    // Runs before every test method
    @BeforeEach
    void cleanDatabase() {
        // @Transactional handles rollback automatically
        // but roles need to persist across tests inside same class
    }

    protected Role getOrCreateRole(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name(roleName).build()));
    }

    protected User createTestUser(String email, String name, String role) {
        User user = User.builder()
                .name(name)
                .email(email)
                .mobileNumber("9876543210")
                .passwordHash(passwordEncoder.encode("Test@1234"))
                .isActive(true)
                .dob(LocalDate.of(1990, 5, 15))
                .build();
        User saved = userRepository.save(user);

        Role userRole = getOrCreateRole(role);
        UserRole ur = UserRole.builder()
                .user(saved)
                .role(userRole)
                .build();
        userRoleRepository.save(ur);

        return saved;
    }

    protected String generateToken(String email) {
        return "Bearer " + jwtTokenProvider.generateTokenFromUsername(email);
    }
}
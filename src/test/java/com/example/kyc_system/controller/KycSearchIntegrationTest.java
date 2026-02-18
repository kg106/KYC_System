package com.example.kyc_system.controller;

import com.example.kyc_system.dto.LoginDTO;
import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.repository.UserRepository;
import com.example.kyc_system.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class KycSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KycRequestRepository kycRequestRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private com.example.kyc_system.repository.RoleRepository roleRepository;

    @Autowired
    private com.example.kyc_system.repository.UserRoleRepository userRoleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Handle Admin User
        User adminUser = userRepository.findByEmail("admin@search.com").orElse(null);

        if (adminUser == null) {
            // Check if mobile exists
            userRepository.findAll().stream()
                    .filter(u -> "9876543210".equals(u.getMobileNumber()))
                    .findFirst()
                    .ifPresent(u -> {
                        kycRequestRepository.deleteAll(kycRequestRepository.findByUserId(u.getId()));
                        userRepository.delete(u);
                    });

            UserDTO adminDto = UserDTO.builder()
                    .name("Admin User")
                    .email("admin@search.com")
                    .password("Admin@123")
                    .mobileNumber("9876543210")
                    .isActive(true)
                    .build();
            UserDTO createdAdmin = userService.createUser(adminDto);

            // Assign ROLE_ADMIN
            User adminEntity = userRepository.findById(createdAdmin.getId()).get();

            // Ensure ROLE_ADMIN exists
            com.example.kyc_system.entity.Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                    .orElseGet(() -> roleRepository.save(new com.example.kyc_system.entity.Role(null, "ROLE_ADMIN")));

            com.example.kyc_system.entity.UserRole userRole = com.example.kyc_system.entity.UserRole.builder()
                    .user(adminEntity)
                    .role(adminRole)
                    .build();
            userRoleRepository.saveAndFlush(userRole);

            // Re-fetch user to ensure roles are updated if needed by subsequent calls in
            // same tx?
            // EntityManager should handle it, but explicit flush helps.
        }

        // Login to get token
        LoginDTO loginDTO = new LoginDTO("admin@search.com", "Admin@123");
        adminToken = userService.login(loginDTO);

        createTestData();
    }

    private void createTestData() {
        // Handle Test User
        if (userRepository.findByEmail("user@search.com").isEmpty()) {
            // Check mobile
            userRepository.findAll().stream()
                    .filter(u -> "9876543211".equals(u.getMobileNumber()))
                    .findFirst()
                    .ifPresent(u -> {
                        kycRequestRepository.deleteAll(kycRequestRepository.findByUserId(u.getId()));
                        userRepository.delete(u);
                    });

            UserDTO userDTO = UserDTO.builder()
                    .name("Test User")
                    .email("user@search.com")
                    .password("User@123")
                    .mobileNumber("9876543211")
                    .isActive(true)
                    .build();
            userService.createUser(userDTO);
        }
        testUser = userRepository.findByEmail("user@search.com").get();

        java.util.List<KycRequest> existing = kycRequestRepository.findByUserId(testUser.getId());
        if (!existing.isEmpty()) {
            kycRequestRepository.deleteAll(existing);
        }

        // Create KYC Requests
        KycRequest request1 = new KycRequest();
        request1.setUser(testUser);
        request1.setDocumentType("PAN_CARD");
        request1.setStatus("PENDING");
        request1.setSubmittedAt(LocalDateTime.now().minusDays(2));
        request1.setAttemptNumber(1);
        kycRequestRepository.save(request1);

        KycRequest request2 = new KycRequest();
        request2.setUser(testUser);
        request2.setDocumentType("AADHAAR_CARD");
        request2.setStatus("APPROVED");
        request2.setSubmittedAt(LocalDateTime.now().minusDays(1));
        request2.setAttemptNumber(1);
        kycRequestRepository.save(request2);
    }

    @Test
    void testSearchKycRequestsByStatus() throws Exception {
        // This test might fail if the user doesn't have ROLE_ADMIN.
        // We will assume for now the token works or we might see 403.
        mockMvc.perform(get("/api/kyc/search")
                .param("status", "PENDING")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void testSearchKycRequestsByDocumentType() throws Exception {
        mockMvc.perform(get("/api/kyc/search")
                .param("documentType", "AADHAAR_CARD")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].documentType").value("AADHAAR_CARD"));
    }

    @Test
    void testSearchUsersByName() throws Exception {
        mockMvc.perform(get("/api/users/search")
                .param("name", "Test User")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Test User"));
    }
}

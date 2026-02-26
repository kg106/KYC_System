package com.example.kyc_system.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KycStatusIntegrationTest extends BaseIntegrationTest {

    private User testUser;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        testUser = createTestUser("status@test.com", "Status User", "ROLE_USER");
        User admin = createTestUser("admin@test.com", "Admin", "ROLE_ADMIN");
        userToken = generateToken(testUser.getEmail());
        adminToken = generateToken(admin.getEmail());
    }

    // ── TEST 1: Status When No KYC Exists ────────────────────────────────────
    @Test
    void getKycStatus_whenNoRequest_returns404() throws Exception {
        mockMvc.perform(get("/api/kyc/status/" + testUser.getId())
                .header("Authorization", userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No KYC request found for this user"));
    }

    // ── TEST 2: Status After Submission ──────────────────────────────────────
    @Test
    void getKycStatus_afterSubmission_returnsCurrentStatus() throws Exception {
        KycRequest req = new KycRequest();
        req.setUser(testUser);
        req.setDocumentType("PAN");
        req.setStatus("SUBMITTED");
        req.setAttemptNumber(1);
        req.setSubmittedAt(LocalDateTime.now());
        kycRequestRepository.save(req);

        mockMvc.perform(get("/api/kyc/status/" + testUser.getId())
                .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.requestId").isNumber());
    }

    // ── TEST 3: Admin sees masked document number ────────────────────────────
    @Test
    void getKycStatus_asAdmin_documentNumberIsMasked() throws Exception {
        // Setup a VERIFIED request with extracted data
        // ... (build full entity graph) ...

        mockMvc.perform(get("/api/kyc/status/" + testUser.getId())
                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractedDocumentNumber")
                        .value(org.hamcrest.Matchers.matchesPattern("\\*+[A-Z0-9]{4}")));
    }
}

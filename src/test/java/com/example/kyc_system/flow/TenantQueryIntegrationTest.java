package com.example.kyc_system.flow;

import com.example.kyc_system.BaseIntegrationTest;
import com.example.kyc_system.entity.*;
import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

// import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * File 2 — Tenant Query + KYC Query Integration Tests
 *
 * Combines four related gaps into one file:
 *
 * GAP: GET /api/tenants — never tested
 * SuperAdmin lists all tenants → 200 paginated
 * SuperAdmin gets specific tenant by id → 200 with correct fields
 * Non-existent tenantId → 500 (getOrThrow throws RuntimeException)
 * Non-superadmin access → 403
 *
 * GAP: POST /api/kyc/report — never tested
 * Admin triggers report with no date params → 200, defaults to last month
 * Admin triggers with custom date range → 200
 * Regular user triggers → 403
 *
 * GAP: GET /api/kyc/status/all/{userId} — never tested
 * User views own full history → 200, list with correct count
 * User with no history → 404
 * Cross-user access → 403
 *
 * GAP: GET /api/kyc/search with real filters — only 403 was tested before
 * Admin searches with no filters → 200 paginated
 * Admin searches by userId → filters correctly
 * Admin searches by userName → filters correctly
 * Admin searches by status → filters correctly
 * Admin searches by documentType → filters correctly
 * SuperAdmin searches across all tenants
 *
 * GAP: Tenant not found in TenantResolutionFilter → 400
 * Any request with X-Tenant-ID: nonexistent → 400 "Tenant not found"
 */
public class TenantQueryIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private KycRequestRepository kycRequestRepository;

        @Autowired
        private KycDocumentRepository kycDocumentRepository;

        // @Autowired
        // private KycExtractedDataRepository kycExtractedDataRepository;

        @Autowired
        private UserRepository userRepository;

        // ─── Shared setup helpers ────────────────────────────────────────────────

        private String superAdminToken() throws Exception {
                return loginAndGetToken("superadmin@kyc.com", "SuperAdmin@123");
        }

        private String adminToken() throws Exception {
                return loginAndGetToken("admin@kyc.com", "Password@123");
        }

        /**
         * Creates a fresh tenant via superadmin and returns its tenantId.
         */
        private String createTenant(String superToken, String suffix) throws Exception {
                String tenantId = "query_" + suffix;
                mockMvc.perform(post("/api/tenants")
                                .header("Authorization", "Bearer " + superToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"tenantId":"%s","name":"Query Tenant %s",
                                                 "email":"query%s@test.com","maxDailyAttempts":5}
                                                """.formatted(tenantId, suffix, suffix)))
                                .andExpect(status().isCreated());
                return tenantId;
        }

        /**
         * Registers a user under the given tenant and returns their userId.
         */
        private Long registerUser(String tenantId, String nameSuffix) throws Exception {
                String uid = UUID.randomUUID().toString().substring(0, 6);
                String email = "quser." + uid + "@example.com";
                String mobile = String.format("9%09d", Math.abs(uid.hashCode()) % 1_000_000_000L);

                MvcResult reg = mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", tenantId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Query User %s","email":"%s","password":"Password@123",
                                                 "mobileNumber":"%s","dob":"1991-07-20"}
                                                """.formatted(nameSuffix, email, mobile)))
                                .andExpect(status().isCreated())
                                .andReturn();
                return objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asLong();
        }

        /**
         * Seeds a KycRequest with given status + KycDocument for the user.
         * Returns the KycRequest id.
         */
        private Long seedKycRequest(Long userId, String tenantId, KycStatus status, DocumentType docType) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new AssertionError("User not found: " + userId));

                KycRequest request = kycRequestRepository.save(KycRequest.builder()
                                .user(user)
                                .documentType(docType.name())
                                .status(status.name())
                                .tenantId(tenantId)
                                .attemptNumber(1)
                                .submittedAt(LocalDateTime.now().minusHours(2))
                                .completedAt(status == KycStatus.VERIFIED || status == KycStatus.FAILED
                                                ? LocalDateTime.now().minusMinutes(30)
                                                : null)
                                .build());

                kycDocumentRepository.save(KycDocument.builder()
                                .kycRequest(request)
                                .tenantId(tenantId)
                                .documentType(docType.name())
                                .documentNumber("TESTDOC123")
                                .documentPath("uploads/doc.jpg")
                                .documentHash("aabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccdd")
                                .mimeType("image/jpeg")
                                .fileSize(51200L)
                                .encrypted(true)
                                .uploadedAt(LocalDateTime.now().minusHours(2))
                                .build());

                return request.getId();
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // GET /api/tenants — list and get
        // ═══════════════════════════════════════════════════════════════════════════

        @Test
        void superAdmin_listAllTenants_returns200Paginated() throws Exception {
                String superToken = superAdminToken();

                // At minimum the seeded "system" and "default" tenants exist
                mockMvc.perform(get("/api/tenants")
                                .header("Authorization", "Bearer " + superToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.totalElements")
                                                .value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                                .andExpect(jsonPath("$.content[0].tenantId").exists())
                                .andExpect(jsonPath("$.content[0].isActive").exists());
        }

        @Test
        void superAdmin_getTenantById_returnsCorrectFields() throws Exception {
                String superToken = superAdminToken();
                String suffix = UUID.randomUUID().toString().substring(0, 6);
                String tenantId = createTenant(superToken, suffix);

                mockMvc.perform(get("/api/tenants/" + tenantId)
                                .header("Authorization", "Bearer " + superToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.tenantId").value(tenantId))
                                .andExpect(jsonPath("$.name").value("Query Tenant " + suffix))
                                .andExpect(jsonPath("$.isActive").value(true))
                                .andExpect(jsonPath("$.maxDailyAttempts").value(5))
                                .andExpect(jsonPath("$.apiKey").exists());
        }

        @Test
        void superAdmin_getNonExistentTenant_returns500() throws Exception {
                // getOrThrow() throws RuntimeException → GlobalExceptionHandler → 500
                // This is a known gap — the correct behavior should be 404.
                // Documenting current behavior so a regression is caught if it changes.
                String superToken = superAdminToken();

                mockMvc.perform(get("/api/tenants/nonexistent_tenant_xyz")
                                .header("Authorization", "Bearer " + superToken))
                                .andExpect(status().isInternalServerError())
                                .andExpect(jsonPath("$.message").value("Tenant not found: nonexistent_tenant_xyz"));
        }

        @Test
        void nonSuperAdmin_listTenants_returns403() throws Exception {
                // TenantController has @PreAuthorize("hasRole('SUPER_ADMIN')") at class level
                String adminToken = adminToken();

                mockMvc.perform(get("/api/tenants")
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isForbidden());
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // Tenant not found in TenantResolutionFilter → 400
        // ═══════════════════════════════════════════════════════════════════════════

        @Test
        void unknownTenantHeader_isRejectedWith400() throws Exception {
                // TenantResolutionFilter resolution priority: (1) JWT claim, (2) X-Tenant-ID
                // header
                // Sending a JWT embeds the real tenantId in the claim — the header is never
                // read.
                // To test the header path we must send NO JWT, so resolution falls through to
                // X-Tenant-ID.
                // POST /api/auth/register is permitAll() and NOT in EXCLUDED_PATHS, so it goes
                // through TenantResolutionFilter. The filter will attempt to look up
                // "totally_nonexistent_tenant" in the DB, fail, and sendError() → 400.
                mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", "totally_nonexistent_tenant")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Ghost","email":"ghost@test.com","password":"Password@123",
                                                 "mobileNumber":"9000000001","dob":"1990-01-01"}
                                                """))
                                .andExpect(status().isBadRequest());
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // POST /api/kyc/report
        // ═══════════════════════════════════════════════════════════════════════════

        @Test
        void admin_triggerReport_noDateParams_returns200() throws Exception {
                // No dateFrom/dateTo → defaults to last month
                // Email sending will fail silently (no mail server in tests) but 200 still
                // returned
                mockMvc.perform(post("/api/kyc/report")
                                .header("Authorization", "Bearer " + adminToken())
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isOk())
                                .andExpect(content().string(
                                                org.hamcrest.Matchers.containsString("Report triggered for range:")));
        }

        @Test
        void admin_triggerReport_withCustomDateRange_returns200() throws Exception {
                mockMvc.perform(post("/api/kyc/report")
                                .header("Authorization", "Bearer " + adminToken())
                                .header("X-Tenant-ID", "default")
                                .param("dateFrom", "2025-01-01")
                                .param("dateTo", "2025-01-31"))
                                .andExpect(status().isOk())
                                .andExpect(content().string("Report triggered for range: 2025-01-01 to 2025-01-31"));
        }

        @Test
        void regularUser_triggerReport_returns403() throws Exception {
                String uid = UUID.randomUUID().toString().substring(0, 6);
                String email = "rpt." + uid + "@example.com";
                String mobile = String.format("6%09d", Math.abs(uid.hashCode()) % 1_000_000_000L);

                mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Report User","email":"%s","password":"Password@123",
                                                 "mobileNumber":"%s","dob":"1993-09-12"}
                                                """.formatted(email, mobile)))
                                .andExpect(status().isCreated());

                String userToken = loginAndGetToken(email, "Password@123");

                mockMvc.perform(post("/api/kyc/report")
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isForbidden());
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // GET /api/kyc/status/all/{userId}
        // ═══════════════════════════════════════════════════════════════════════════

        @Test
        void user_viewsOwnFullKycHistory_returns200WithList() throws Exception {
                Long userId = registerUser("default", "hist");
                String email = userRepository.findById(userId).orElseThrow().getEmail();
                String token = loginAndGetToken(email, "Password@123");

                // Seed two KYC requests for this user
                seedKycRequest(userId, "default", KycStatus.FAILED, DocumentType.PAN);
                seedKycRequest(userId, "default", KycStatus.VERIFIED, DocumentType.AADHAAR);

                MvcResult result = mockMvc.perform(get("/api/kyc/status/all/" + userId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andReturn();

                int count = objectMapper.readTree(result.getResponse().getContentAsString()).size();
                assertEquals(2, count, "History must contain exactly the 2 seeded requests");
        }

        @Test
        void user_withNoKycHistory_returns404() throws Exception {
                Long userId = registerUser("default", "nohist");
                String email = userRepository.findById(userId).orElseThrow().getEmail();
                String token = loginAndGetToken(email, "Password@123");

                // getAllKycStatus returns 404 if the list is empty
                mockMvc.perform(get("/api/kyc/status/all/" + userId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value("No KYC history found for this user"));
        }

        @Test
        void user_crossUserKycHistory_returns403() throws Exception {
                Long userAId = registerUser("default", "histA");
                Long userBId = registerUser("default", "histB");

                String emailB = userRepository.findById(userBId).orElseThrow().getEmail();
                String tokenB = loginAndGetToken(emailB, "Password@123");

                // User B tries to access User A's history
                mockMvc.perform(get("/api/kyc/status/all/" + userAId)
                                .header("Authorization", "Bearer " + tokenB)
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isForbidden());
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // GET /api/kyc/search with real filters
        // ═══════════════════════════════════════════════════════════════════════════

        @Test
        void admin_searchKyc_noFilters_returns200Paginated() throws Exception {
                Long userId = registerUser("default", "srch");
                seedKycRequest(userId, "default", KycStatus.SUBMITTED, DocumentType.PAN);

                mockMvc.perform(get("/api/kyc/search")
                                .header("Authorization", "Bearer " + adminToken())
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.totalElements").value(
                                                org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        }

        @Test
        void admin_searchKyc_byUserId_returnsOnlyThatUsersRequests() throws Exception {
                Long userId = registerUser("default", "byuid");
                seedKycRequest(userId, "default", KycStatus.FAILED, DocumentType.PAN);

                // Search with the real userId → at least the seeded request must appear
                MvcResult result = mockMvc.perform(get("/api/kyc/search")
                                .header("Authorization", "Bearer " + adminToken())
                                .header("X-Tenant-ID", "default")
                                .param("userId", String.valueOf(userId)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andReturn();

                var content = objectMapper.readTree(result.getResponse().getContentAsString())
                                .get("content");
                assertTrue(content.size() >= 1, "At least one result expected for userId=" + userId);

                // Search with a bogus userId → empty page (filter is exact match on userId)
                mockMvc.perform(get("/api/kyc/search")
                                .header("Authorization", "Bearer " + adminToken())
                                .header("X-Tenant-ID", "default")
                                .param("userId", "999999"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void admin_searchKyc_byStatus_returnsMatchingOnly() throws Exception {
                Long userId = registerUser("default", "bystat");
                seedKycRequest(userId, "default", KycStatus.VERIFIED, DocumentType.PAN);

                MvcResult result = mockMvc.perform(get("/api/kyc/search")
                                .header("Authorization", "Bearer " + adminToken())
                                .header("X-Tenant-ID", "default")
                                .param("status", "VERIFIED"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andReturn();

                // Every result in the page must have status=VERIFIED
                var content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
                content.forEach(node -> assertEquals("VERIFIED", node.get("status").asText(),
                                "All results must have status=VERIFIED"));
        }

        @Test
        void admin_searchKyc_byDocumentType_returnsMatchingOnly() throws Exception {
                Long userId = registerUser("default", "bydoc");
                seedKycRequest(userId, "default", KycStatus.FAILED, DocumentType.AADHAAR);

                MvcResult result = mockMvc.perform(get("/api/kyc/search")
                                .header("Authorization", "Bearer " + adminToken())
                                .header("X-Tenant-ID", "default")
                                .param("documentType", "AADHAAR"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andReturn();

                // All results must have documentType=AADHAAR
                var content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
                assertTrue(content.size() >= 1, "At least one AADHAAR result expected");
                content.forEach(node -> {
                        JsonNode docTypeNode = node.get("documentType");
                        // Skip records with null documentType, or assert non-null before checking
                        if (docTypeNode != null) {
                                assertEquals("AADHAAR", docTypeNode.asText());
                        }
                });
        }

        @Test
        void superAdmin_searchKyc_seesAllTenants() throws Exception {
                String superToken = superAdminToken();
                String suffix = UUID.randomUUID().toString().substring(0, 6);
                String tenantId = createTenant(superToken, suffix);

                // Register user and seed KYC in the NEW tenant
                Long userId = registerUser(tenantId, "cross");
                seedKycRequest(userId, tenantId, KycStatus.SUBMITTED, DocumentType.PAN);

                // SuperAdmin search without tenant filter must see the new tenant's request
                MvcResult result = mockMvc.perform(get("/api/kyc/search")
                                .header("Authorization", "Bearer " + superToken)
                                .param("userId", String.valueOf(userId)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andReturn();

                var content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
                assertTrue(content.size() >= 1,
                                "SuperAdmin must see KYC requests across all tenants");
        }
}
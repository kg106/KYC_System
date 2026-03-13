package com.example.kyc_system.flow;

import com.example.kyc_system.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Journey 6 — Multi-Tenant Data Isolation
 *
 * Verifies that tenant boundaries are enforced at every layer:
 *
 * 1. Setup — Create two completely independent tenants (A and B),
 * each with their own admin and regular user.
 *
 * 2. Rule 1 — User A's JWT token cannot access User B's KYC status.
 * The JWT contains tenantId=A, so TenantResolutionFilter scopes
 * the request to Tenant A. User B belongs to Tenant B.
 * @PreAuthorize("@securityService.canAccessUser(#userId)")
 * → looks up User B in Tenant A's scope → not found → 403.
 *
 * 3. Rule 2 — Tenant Admin A cannot list users of Tenant B.
 * GET /api/users with Admin A's JWT → TenantContext = Tenant A
 * → UserServiceImpl.getAllUsers() scopes by tenantId=A
 * → response contains only Tenant A users, never Tenant B users.
 *
 * 4. Rule 3 — API key resolves the correct tenant.
 * X-API-Key header for Tenant B → TenantResolutionFilter sets
 * TenantContext = Tenant B → register endpoint works under B.
 *
 * 5. Rule 4 — Cross-tenant KYC upload attempt is rejected.
 * User A's token used to upload KYC with userId = User B's ID → 403.
 * Even if User B's ID is known, the security layer blocks it.
 */
public class MultiTenantIsolationIntegrationTest extends BaseIntegrationTest {

    // Minimal valid 1×1 JPEG — same as KycBusinessRulesIntegrationTest
    private static final byte[] MINIMAL_JPEG = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
            0x00, 0x01, 0x00, 0x00, (byte) 0xFF, (byte) 0xD9
    };

    private MockMultipartFile makeJpeg() {
        return new MockMultipartFile("file", "doc.jpg", "image/jpeg", MINIMAL_JPEG);
    }

    @Test
    void testMultiTenantDataIsolation() throws Exception {

        String suffix = UUID.randomUUID().toString().substring(0, 6);

        // ── Setup: login as super admin ───────────────────────────────────────────
        MvcResult superLogin = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"superadmin@kyc.com","password":"SuperAdmin@123"}
                        """))
                .andExpect(status().isOk())
                .andReturn();
        String superToken = objectMapper.readTree(superLogin.getResponse().getContentAsString())
                .get("accessToken").asText();

        // ── Setup: Create Tenant A ────────────────────────────────────────────────
        String tenantAId = "tenant_a_" + suffix;
        String adminAEmail = "admin.a." + suffix + "@example.com";
        String adminAPass = "AdminA@123";

        MvcResult createTenantA = mockMvc.perform(post("/api/tenants")
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "tenantId": "%s",
                          "name": "Tenant Alpha",
                          "email": "contact@tenantA.com",
                          "adminEmail": "%s",
                          "adminPassword": "%s",
                          "allowedDocumentTypes": ["PAN", "AADHAAR"]
                        }
                        """.formatted(tenantAId, adminAEmail, adminAPass)))
                .andExpect(status().isCreated())
                .andReturn();

        // ── Setup: Create Tenant B ────────────────────────────────────────────────
        String tenantBId = "tenant_b_" + suffix;
        String adminBEmail = "admin.b." + suffix + "@example.com";
        String adminBPass = "AdminB@123";

        MvcResult createTenantB = mockMvc.perform(post("/api/tenants")
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "tenantId": "%s",
                          "name": "Tenant Beta",
                          "email": "contact@tenantB.com",
                          "adminEmail": "%s",
                          "adminPassword": "%s",
                          "allowedDocumentTypes": ["PAN", "AADHAAR"]
                        }
                        """.formatted(tenantBId, adminBEmail, adminBPass)))
                .andExpect(status().isCreated())
                .andReturn();

        // Extract Tenant B's API key for Rule 3
        String tenantBApiKey = objectMapper.readTree(
                createTenantB.getResponse().getContentAsString())
                .get("apiKey").asText();
        assertNotNull(tenantBApiKey, "Tenant B must have an API key");

        // ── Setup: Register User A under Tenant A ─────────────────────────────────
        String userAEmail = "user.a." + suffix + "@example.com";
        String userAPass = "UserA@123";
        String mobileA = String.format("7%09d", Math.abs(userAEmail.hashCode()) % 1_000_000_000L);

        MvcResult regA = mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", tenantAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"User Alpha","email":"%s","password":"%s",
                         "mobileNumber":"%s","dob":"1992-03-10"}
                        """.formatted(userAEmail, userAPass, mobileA)))
                .andExpect(status().isCreated())
                .andReturn();
        Long userAId = objectMapper.readTree(regA.getResponse().getContentAsString())
                .get("id").asLong();

        // ── Setup: Register User B under Tenant B ─────────────────────────────────
        String userBEmail = "user.b." + suffix + "@example.com";
        String userBPass = "UserB@123";
        String mobileB = String.format("6%09d", Math.abs(userBEmail.hashCode()) % 1_000_000_000L);

        MvcResult regB = mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", tenantBId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"User Beta","email":"%s","password":"%s",
                         "mobileNumber":"%s","dob":"1993-07-22"}
                        """.formatted(userBEmail, userBPass, mobileB)))
                .andExpect(status().isCreated())
                .andReturn();
        Long userBId = objectMapper.readTree(regB.getResponse().getContentAsString())
                .get("id").asLong();

        // ── Setup: Login both users to get their JWT tokens ───────────────────────
        MvcResult loginA = mockMvc.perform(post("/api/auth/login")
                .header("X-Tenant-ID", tenantAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(userAEmail, userAPass)))
                .andExpect(status().isOk())
                .andReturn();
        String tokenA = objectMapper.readTree(loginA.getResponse().getContentAsString())
                .get("accessToken").asText();

        MvcResult loginAdminA = mockMvc.perform(post("/api/auth/login")
                .header("X-Tenant-ID", tenantAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(adminAEmail, adminAPass)))
                .andExpect(status().isOk())
                .andReturn();
        String adminAToken = objectMapper.readTree(loginAdminA.getResponse().getContentAsString())
                .get("accessToken").asText();

        // ── Rule 1: User A's JWT cannot access User B's KYC status ───────────────
        // Business: A user's token is scoped to their tenant.
        // User B belongs to Tenant B. User A's JWT has tenantId=A.
        // TenantResolutionFilter sets TenantContext=A.
        // securityService.canAccessUser(userBId) looks up User B in Tenant A → not
        // found → 403.
        mockMvc.perform(get("/api/kyc/status/" + userBId)
                .header("Authorization", "Bearer " + tokenA)
                .header("X-Tenant-ID", tenantAId))
                .andExpect(status().isForbidden());

        // ── Rule 2: Tenant Admin A cannot see Tenant B's users ───────────────────
        // Business: GET /api/users scoped by TenantContext.
        // Admin A's JWT sets TenantContext=A → only Tenant A users returned.
        // User B must NOT appear in the response.
        MvcResult usersResult = mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + adminAToken)
                .header("X-Tenant-ID", tenantAId))
                .andExpect(status().isOk())
                .andReturn();

        String usersBody = usersResult.getResponse().getContentAsString();
        // Every user in the response must belong to Tenant A
        objectMapper.readTree(usersBody).forEach(user -> {
            String tenantId = user.path("tenantId").asText();
            assertEquals(tenantAId, tenantId,
                    "Admin A must only see Tenant A users, but found user from: " + tenantId);
        });
        // Confirm User B's email is not in the list
        assertFalse(usersBody.contains(userBEmail),
                "Tenant B's user must not be visible to Tenant A's admin");

        // ── Rule 3: API key resolves correct tenant ────────────────────────────────
        // Business: X-API-Key header is used by external integrations.
        // Tenant B's API key should resolve TenantContext = Tenant B.
        // Registering a user with only the API key header (no JWT, no X-Tenant-ID)
        // should succeed under Tenant B.
        String apiKeyUserEmail = "apikey.user." + suffix + "@example.com";
        String apiKeyMobile = String.format("9%09d", Math.abs(apiKeyUserEmail.hashCode()) % 1_000_000_000L);

        mockMvc.perform(post("/api/auth/register")
                .header("X-API-Key", tenantBApiKey) // ← No X-Tenant-ID, only API key
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"API Key User","email":"%s","password":"ApiKey@123",
                         "mobileNumber":"%s","dob":"1995-11-05"}
                        """.formatted(apiKeyUserEmail, apiKeyMobile)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenantBId));

        // ── Rule 4: Cross-tenant KYC upload is rejected ───────────────────────────
        // Business: User A's token (tenantId=A) cannot upload KYC for User B's ID.
        // securityService.canAccessUser(userBId):
        // - User B exists in Tenant B, not Tenant A
        // - Regular user check: currentEmail(A) ≠ User B's email → false
        // → Spring Security throws AccessDeniedException → 403
        mockMvc.perform(multipart("/api/kyc/upload")
                .file(makeJpeg())
                .param("userId", String.valueOf(userBId))
                .param("documentType", "PAN")
                .param("documentNumber", "ABCDE1234F")
                .header("Authorization", "Bearer " + tokenA) // ← Tenant A's token
                .header("X-Tenant-ID", tenantAId))
                .andExpect(status().isForbidden());

        // ── Sanity: User A can still upload KYC for themselves ────────────────────
        // Confirms that the isolation rules didn't break legitimate access.
        mockMvc.perform(multipart("/api/kyc/upload")
                .file(makeJpeg())
                .param("userId", String.valueOf(userAId))
                .param("documentType", "PAN")
                .param("documentNumber", "ABCDE1234F")
                .header("Authorization", "Bearer " + tokenA)
                .header("X-Tenant-ID", tenantAId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").exists());
    }
}
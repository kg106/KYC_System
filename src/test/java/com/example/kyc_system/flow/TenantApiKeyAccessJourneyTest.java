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
 * Journey 9 — Tenant API Key Access Journey
 *
 * Tests the X-API-Key header as a fully independent auth path in
 * TenantResolutionFilter (Priority 3), which was never exercised in isolation.
 *
 * What X-API-Key does:
 * TenantResolutionFilter Priority 3: findByApiKey(key) → sets TenantContext.
 * It resolves the TENANT only — it does NOT authenticate a user.
 * A JWT is still required for endpoints protected by @PreAuthorize.
 *
 * What X-API-Key does NOT bypass:
 * - Spring Security authentication (JWT still required for protected endpoints)
 * - @PreAuthorize / RBAC checks
 *
 * Scenarios tested:
 *
 * Step 1 — Register user using X-API-Key (no X-Tenant-ID, no JWT)
 * /api/auth/register is excluded from TenantResolutionFilter for JWT,
 * but NOT excluded from tenant resolution. API key resolves the tenant.
 * Result: 201, user.tenantId = new tenant.
 *
 * Step 2 — Login and get JWT (needed for protected endpoints)
 * Login requires tenant context. Send X-API-Key alongside login.
 * Result: valid JWT with tenantId embedded.
 *
 * Step 3 — KYC upload using X-API-Key for tenant + JWT for auth
 * X-API-Key resolves tenant. JWT authenticates user.
 * Since JWT has tenantId claim (Priority 1), X-API-Key is effectively
 * redundant here but still sent to explicitly test the path.
 * Result: 202 Accepted.
 *
 * Step 4 — Rotate API key (Super Admin only)
 * POST /api/tenants/{tenantId}/rotate-api-key
 * Old key is immediately invalidated in DB.
 * Result: new API key returned.
 *
 * Step 5 — Old API key rejected after rotation
 * TenantResolutionFilter: findByApiKey(oldKey) → empty → tenantId = null
 * → sendError(400, "X-Tenant-ID header is required").
 * Result: 400 Bad Request.
 *
 * Step 6 — New API key works for tenant resolution
 * Register a second user using only the new API key.
 * Result: 201, user.tenantId = same tenant.
 */
public class TenantApiKeyAccessJourneyTest extends BaseIntegrationTest {

    private static final byte[] MINIMAL_JPEG = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
            0x00, 0x01, 0x00, 0x00, (byte) 0xFF, (byte) 0xD9
    };

    @Test
    void testTenantApiKeyAccessJourney() throws Exception {

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // ── Setup: Login as Super Admin ───────────────────────────────────────────
        MvcResult superLogin = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"superadmin@kyc.com","password":"SuperAdmin@123"}
                        """))
                .andExpect(status().isOk())
                .andReturn();
        String superToken = objectMapper.readTree(superLogin.getResponse().getContentAsString())
                .get("accessToken").asText();

        // ── Setup: Create a new tenant ────────────────────────────────────────────
        String tenantId = "apikey_tenant_" + suffix;

        MvcResult createTenant = mockMvc.perform(post("/api/tenants")
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "tenantId": "%s",
                          "name": "API Key Tenant",
                          "email": "contact@apikeytenant.com",
                          "allowedDocumentTypes": ["PAN", "AADHAAR"]
                        }
                        """.formatted(tenantId)))
                .andExpect(status().isCreated())
                .andReturn();

        String createBody = createTenant.getResponse().getContentAsString();
        String originalApiKey = objectMapper.readTree(createBody).get("apiKey").asText();
        assertNotNull(originalApiKey, "Tenant must have an API key on creation");
        assertFalse(originalApiKey.isBlank(), "API key must not be blank");

        // ── Step 1: Register user using ONLY X-API-Key (no X-Tenant-ID, no JWT) ──
        // TenantResolutionFilter Priority 3: findByApiKey → sets TenantContext.
        // /api/auth/register is not JWT-protected, so this works without a token.
        String userEmail = "apikey.user." + suffix + "@example.com";
        String userPass = "ApiKey@123";
        String userMobile = String.format("7%09d", Math.abs(userEmail.hashCode()) % 1_000_000_000L);

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                .header("X-API-Key", originalApiKey) // ← No X-Tenant-ID, no JWT
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"ApiKey User","email":"%s","password":"%s",
                         "mobileNumber":"%s","dob":"1994-08-20"}
                        """.formatted(userEmail, userPass, userMobile)))
                .andExpect(status().isCreated())
                .andReturn();

        String regBody = regResult.getResponse().getContentAsString();
        Long userId = objectMapper.readTree(regBody).get("id").asLong();
        // Tenant resolved via API key — user must be scoped to correct tenant
        String registeredTenantId = objectMapper.readTree(regBody).get("tenantId").asText();
        assertEquals(tenantId, registeredTenantId,
                "Step 1: User registered via API key must belong to the correct tenant");

        // ── Step 2: Login using X-API-Key for tenant resolution ───────────────────
        // /api/auth/login is in EXCLUDED_PATHS of TenantResolutionFilter,
        // so tenant resolution doesn't apply here — but the user's tenantId
        // is already embedded in the JWT from registration.
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .header("X-Tenant-ID", tenantId) // needed for login tenant context
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(userEmail, userPass)))
                .andExpect(status().isOk())
                .andReturn();

        String userJwt = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();
        assertNotNull(userJwt, "Step 2: JWT must be returned after login");

        // ── Step 3: KYC upload using X-API-Key for tenant + JWT for auth ──────────
        // X-API-Key → TenantResolutionFilter resolves tenant (Priority 3).
        // JWT → JwtAuthenticationFilter authenticates the user.
        // Note: JWT already contains tenantId (Priority 1 wins), but we send
        // X-API-Key explicitly to prove it's a valid path.
        MockMultipartFile doc = new MockMultipartFile(
                "file", "pan.jpg", "image/jpeg", MINIMAL_JPEG);

        mockMvc.perform(multipart("/api/kyc/upload")
                .file(doc)
                .param("userId", String.valueOf(userId))
                .param("documentType", "PAN")
                .param("documentNumber", "ABCDE1234F")
                .header("Authorization", "Bearer " + userJwt)
                .header("X-API-Key", originalApiKey)) // ← API key instead of X-Tenant-ID
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.message").value("KYC request submitted successfully"));

        // ── Step 4: Rotate API key (Super Admin only) ─────────────────────────────
        // POST /api/tenants/{tenantId}/rotate-api-key
        // Old key is immediately overwritten in DB. findByApiKey(oldKey) → empty.
        MvcResult rotateResult = mockMvc.perform(
                post("/api/tenants/" + tenantId + "/rotate-api-key")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andReturn();

        String newApiKey = objectMapper.readTree(rotateResult.getResponse().getContentAsString())
                .get("apiKey").asText();
        assertNotNull(newApiKey, "Step 4: Rotation must return a new API key");
        assertNotEquals(originalApiKey, newApiKey,
                "Step 4: New API key must be different from the old one");

        // ── Step 5: Old API key is rejected after rotation ────────────────────────
        // TenantResolutionFilter: findByApiKey(oldKey) → Optional.empty() → null
        // → tenantId = null → sendError(400, "X-Tenant-ID header is required")
        String secondUserEmail = "apikey.user2." + suffix + "@example.com";
        String secondMobile = String.format("8%09d", Math.abs(secondUserEmail.hashCode()) % 1_000_000_000L);

        MvcResult oldKeyResult = mockMvc.perform(post("/api/auth/register")
                .header("X-API-Key", originalApiKey) // ← OLD key, now invalid
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Old Key User","email":"%s","password":"OldKey@123",
                         "mobileNumber":"%s","dob":"1995-03-10"}
                        """.formatted(secondUserEmail, secondMobile)))
                .andReturn();

        assertEquals(400, oldKeyResult.getResponse().getStatus(),
                "Step 5: Old API key must be rejected after rotation (400 Bad Request)");
        assertTrue(oldKeyResult.getResponse().getContentAsString().contains("X-Tenant-ID"),
                "Step 5: Error message must indicate missing tenant resolution");

        // ── Step 6: New API key successfully resolves tenant ──────────────────────
        // findByApiKey(newKey) → tenant found → TenantContext set correctly
        // Registration under new key must succeed and be scoped to correct tenant
        String thirdUserEmail = "apikey.user3." + suffix + "@example.com";
        String thirdMobile = String.format("9%09d", Math.abs(thirdUserEmail.hashCode()) % 1_000_000_000L);

        MvcResult newKeyResult = mockMvc.perform(post("/api/auth/register")
                .header("X-API-Key", newApiKey) // ← NEW key
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"New Key User","email":"%s","password":"NewKey@123",
                         "mobileNumber":"%s","dob":"1996-11-25"}
                        """.formatted(thirdUserEmail, thirdMobile)))
                .andExpect(status().isCreated())
                .andReturn();

        String newKeyRegBody = newKeyResult.getResponse().getContentAsString();
        String newKeyUserTenantId = objectMapper.readTree(newKeyRegBody).get("tenantId").asText();
        assertEquals(tenantId, newKeyUserTenantId,
                "Step 6: New API key must correctly resolve tenant — user scoped to correct tenant");
    }
}
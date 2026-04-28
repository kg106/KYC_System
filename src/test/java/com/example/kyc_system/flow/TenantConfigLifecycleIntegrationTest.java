package com.example.kyc_system.flow;

import com.example.kyc_system.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class TenantConfigLifecycleIntegrationTest extends BaseIntegrationTest {

    @Test
    void testTenantConfigurationLifecycle() throws Exception {
        String tenantId = "lifecycle_" + UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "admin." + tenantId + "@example.com";
        String adminPass = "Admin@1234";
        String userEmail = "user." + tenantId + "@example.com";
        String userPass = "User@1234";
        String userMobile = String.format("9%09d", Math.abs(userEmail.hashCode()) % 1_000_000_000L);

        // Login as superadmin
        MvcResult superLogin = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"superadmin@kyc.com","password":"SuperAdmin@123"}
                        """))
                .andExpect(status().isOk())
                .andReturn();
        String superToken = objectMapper.readTree(superLogin.getResponse().getContentAsString())
                .get("accessToken").asText();

        // ── Step 1: Non-superadmin cannot access tenant endpoints → 403 ──────────
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                .header("X-Tenant-ID", "default")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"admin@kyc.com","password":"Password@123"}
                        """))
                .andExpect(status().isOk())
                .andReturn();
        String tenantAdminToken = objectMapper.readTree(adminLogin.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/tenants")
                .header("Authorization", "Bearer " + tenantAdminToken))
                .andExpect(status().isForbidden());

        // ── Step 2: SuperAdmin creates a new tenant with admin credentials → 201 ──
        MvcResult createResult = mockMvc.perform(post("/api/tenants")
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "tenantId":"%s",
                          "name":"Lifecycle Tenant",
                          "email":"ops@lifecycle.com",
                          "maxDailyAttempts":3,
                          "allowedDocumentTypes":["PAN","AADHAAR"],
                          "adminEmail":"%s",
                          "adminPassword":"%s"
                        }
                        """.formatted(tenantId, adminEmail, adminPass)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenantId))
                .andExpect(jsonPath("$.maxDailyAttempts").value(3))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.apiKey").exists())
                .andReturn();

        String originalApiKey = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("apiKey").asText();

        // ── Step 3: Creating duplicate tenant → 409 BusinessException ─────────────
        mockMvc.perform(post("/api/tenants")
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "tenantId":"%s",
                          "name":"Duplicate","email":"dup@test.com"
                        }
                        """.formatted(tenantId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));

        // ── Step 4: Update tenant config — reduce maxDailyAttempts to 1 → 200 ────
        mockMvc.perform(patch("/api/tenants/" + tenantId)
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"maxDailyAttempts":1,"name":"Lifecycle Tenant Updated"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxDailyAttempts").value(1))
                .andExpect(jsonPath("$.name").value("Lifecycle Tenant Updated"));

        // ── Step 5: Register a user under the new tenant → 201 ───────────────────
        mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Lifecycle User","email":"%s","password":"%s",
                         "mobileNumber":"%s","dob":"1995-01-01"}
                        """.formatted(userEmail, userPass, userMobile)))
                .andExpect(status().isCreated());

        // ── Step 6: Get tenant stats — totalUsers should be 2 (admin + user) ─────
        mockMvc.perform(get("/api/tenants/" + tenantId + "/stats")
                .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(2))
                .andExpect(jsonPath("$.tenantId").value(tenantId));

        // ── Step 7: Deactivate the tenant → 200, changed=true ────────────────────
        mockMvc.perform(patch("/api/tenants/" + tenantId + "/deactivate")
                .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(content().string("Tenant deactivated: " + tenantId));

        // ── Step 8: Deactivate again (idempotent) → 200, already inactive message ─
        mockMvc.perform(patch("/api/tenants/" + tenantId + "/deactivate")
                .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(content().string("Tenant is already inactive: " + tenantId));

        // ── Step 9: TenantResolutionFilter blocks requests to inactive tenant → 400
        // Register is NOT in EXCLUDED_PATHS so the filter runs and rejects it
        mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"New User","email":"new@test.com","password":"Pass@1234",
                         "mobileNumber":"9000000001","dob":"1995-01-01"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Tenant is inactive: " + tenantId));

        // ── Step 10: Login IS excluded from filter — still returns 200 ─────────────
        // /api/auth/login is in EXCLUDED_PATHS, so inactive tenant check is skipped
        mockMvc.perform(post("/api/auth/login")
                .header("X-Tenant-ID", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(userEmail, userPass)))
                .andExpect(status().isOk());

        // ── Step 11: Reactivate tenant → 200, changed=true ───────────────────────
        mockMvc.perform(patch("/api/tenants/" + tenantId + "/activate")
                .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(content().string("Tenant activated: " + tenantId));

        // ── Step 12: Activate again (idempotent) → 200, already active message ────
        mockMvc.perform(patch("/api/tenants/" + tenantId + "/activate")
                .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(content().string("Tenant is already active: " + tenantId));

        // ── Step 13: Register succeeds again after reactivation ───────────────────
        mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Reactivated User","email":"reactivated.%s@test.com",
                         "password":"Pass@1234","mobileNumber":"9000000002","dob":"1995-01-01"}
                        """.formatted(uniqueId())))
                .andExpect(status().isCreated());

        // ── Step 14: Rotate API key → 200, new key is different from original ─────
        MvcResult rotateResult = mockMvc.perform(post("/api/tenants/" + tenantId + "/rotate-api-key")
                .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").exists())
                .andReturn();

        String newApiKey = objectMapper.readTree(rotateResult.getResponse().getContentAsString())
                .get("apiKey").asText();

        assertNotEquals(originalApiKey, newApiKey, "Rotated API key must differ from original");
        assertTrue(newApiKey.startsWith("kyc_"), "API key must start with 'kyc_'");

        // ── Step 15: Old API key no longer resolves the tenant → 400 ─────────────
        // TenantResolutionFilter looks up tenant by apiKey — old key is gone
        mockMvc.perform(post("/api/auth/register")
                .header("X-API-Key", originalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Test","email":"t@t.com","password":"Pass@1234",
                         "mobileNumber":"9000000003","dob":"1995-01-01"}
                        """))
                .andExpect(status().isBadRequest());

        // ── Step 16: New API key resolves the tenant correctly → 201 ─────────────
        mockMvc.perform(post("/api/auth/register")
                .header("X-API-Key", newApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"API Key User","email":"apikey.%s@test.com",
                         "password":"Pass@1234","mobileNumber":"9000000004","dob":"1995-01-01"}
                        """.formatted(uniqueId())))
                .andExpect(status().isCreated());
    }

    private String uniqueId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
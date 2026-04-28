package com.example.kyc_system.flow;

import com.example.kyc_system.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class UserSelfServiceIntegrationTest extends BaseIntegrationTest {

    @Test
    void testUserSelfServiceAndAdminManagement() throws Exception {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);

        // ── Setup: register two regular users and login as tenant admin ───────────
        String userAEmail = "user.a." + uniqueId + "@example.com";
        String userBEmail = "user.b." + uniqueId + "@example.com";
        String password = "Password@123";
        String mobileA = String.format("6%09d", Math.abs(userAEmail.hashCode()) % 1_000_000_000L);
        String mobileB = String.format("6%09d", Math.abs(userBEmail.hashCode()) % 1_000_000_000L);

        // Register User A
        MvcResult regA = mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", "default")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"User Alpha","email":"%s","password":"%s",
                         "mobileNumber":"%s","dob":"1992-03-10"}
                        """.formatted(userAEmail, password, mobileA)))
                .andExpect(status().isCreated())
                .andReturn();
        Long userAId = objectMapper.readTree(regA.getResponse().getContentAsString()).get("id").asLong();

        // Register User B
        MvcResult regB = mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", "default")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"User Beta","email":"%s","password":"%s",
                         "mobileNumber":"%s","dob":"1993-07-20"}
                        """.formatted(userBEmail, password, mobileB)))
                .andExpect(status().isCreated())
                .andReturn();
        Long userBId = objectMapper.readTree(regB.getResponse().getContentAsString()).get("id").asLong();

        // Login as User A
        MvcResult loginA = mockMvc.perform(post("/api/auth/login")
                .header("X-Tenant-ID", "default")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(userAEmail, password)))
                .andExpect(status().isOk())
                .andReturn();
        String tokenA = objectMapper.readTree(loginA.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Login as tenant admin (seeded: admin@kyc.com / Password@123)
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                .header("X-Tenant-ID", "default")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"admin@kyc.com","password":"Password@123"}
                        """))
                .andExpect(status().isOk())
                .andReturn();
        String adminToken = objectMapper.readTree(adminLogin.getResponse().getContentAsString())
                .get("accessToken").asText();

        // ── Step 1: User A reads their OWN profile → 200 ─────────────────────────
        mockMvc.perform(get("/api/users/" + userAId)
                .header("Authorization", "Bearer " + tokenA)
                .header("X-Tenant-ID", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(userAEmail))
                .andExpect(jsonPath("$.password").doesNotExist()); // never returned

        // ── Step 2: User A updates their OWN name → 200, name changed ────────────
        mockMvc.perform(patch("/api/users/" + userAId)
                .header("Authorization", "Bearer " + tokenA)
                .header("X-Tenant-ID", "default")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Alpha Updated"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha Updated"))
                .andExpect(jsonPath("$.email").value(userAEmail)); // other fields unchanged

        // ── Step 3: User A tries to read User B's profile → 403 ──────────────────
        // securityService.canAccessUser() returns false — different user, not admin
        mockMvc.perform(get("/api/users/" + userBId)
                .header("Authorization", "Bearer " + tokenA)
                .header("X-Tenant-ID", "default"))
                .andExpect(status().isForbidden());

        // ── Step 4: User A tries to update User B's profile → 403 ────────────────
        mockMvc.perform(patch("/api/users/" + userBId)
                .header("Authorization", "Bearer " + tokenA)
                .header("X-Tenant-ID", "default")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Hacked Name"}
                        """))
                .andExpect(status().isForbidden());

        // ── Step 5: User A tries to list ALL users (admin-only) → 403 ─────────────
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + tokenA)
                .header("X-Tenant-ID", "default"))
                .andExpect(status().isForbidden());

        // ── Step 6: User A tries to search users (admin-only) → 403 ──────────────
        mockMvc.perform(get("/api/users/search")
                .header("Authorization", "Bearer " + tokenA)
                .header("X-Tenant-ID", "default"))
                .andExpect(status().isForbidden());

        // ── Step 7: User A tries to delete User B (admin-only) → 403 ─────────────
        mockMvc.perform(delete("/api/users/" + userBId)
                .header("Authorization", "Bearer " + tokenA)
                .header("X-Tenant-ID", "default"))
                .andExpect(status().isForbidden());

        // ── Step 8: Admin lists all users → 200, sees both users + seeded admin ───
        MvcResult listResult = mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", "default"))
                .andExpect(status().isOk())
                .andReturn();
        String listBody = listResult.getResponse().getContentAsString();
        assertTrue(listBody.contains(userAEmail), "Admin list must include User A");
        assertTrue(listBody.contains(userBEmail), "Admin list must include User B");

        // ── Step 9: Admin searches by name → 200, filtered results ───────────────
        mockMvc.perform(get("/api/users/search")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", "default")
                .param("name", "Alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value(userAEmail));

        // ── Step 10: Admin searches by isActive=true → 200 ───────────────────────
        mockMvc.perform(get("/api/users/search")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", "default")
                .param("isActive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        // ── Step 11: Admin deactivates User A via PATCH isActive=false → 200 ──────
        mockMvc.perform(patch("/api/users/" + userAId)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", "default")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"isActive":false}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        // ── Step 12: Deactivated User A can still login — isActive flag is NOT
        // checked in UserDetailsServiceImpl.loadUserByUsername() ─────────
        // This is a known gap: Spring Security's disabled check requires passing
        // user.isActive() as the 'enabled' flag to UserDetails constructor, which
        // this implementation does not do. Document the actual behavior.
        mockMvc.perform(post("/api/auth/login")
                .header("X-Tenant-ID", "default")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(userAEmail, password)))
                .andExpect(status().isUnauthorized()); // BUG: should be 401/403, but isActive is not enforced

        // ── Step 13: Admin deletes User B → 200 ──────────────────────────────────
        mockMvc.perform(delete("/api/users/" + userBId)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", "default"))
                .andExpect(status().isOk())
                .andExpect(content().string("User deleted successfully"));

        // ── Step 14: Deleted User B can no longer login → 401 ────────────────────
        // loadUserByUsername throws UsernameNotFoundException → BadCredentialsException
        // → 401
        mockMvc.perform(post("/api/auth/login")
                .header("X-Tenant-ID", "default")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(userBEmail, password)))
                .andExpect(status().isUnauthorized());

        // ── Step 15: Admin list no longer contains User B ─────────────────────────
        MvcResult afterDeleteList = mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", "default"))
                .andExpect(status().isOk())
                .andReturn();
        String afterBody = afterDeleteList.getResponse().getContentAsString();
        assertFalse(afterBody.contains(userBEmail), "Deleted User B must not appear in user list");
        assertTrue(afterBody.contains(userAEmail), "User A must still be in the list");

        // ── Step 16: Patch with invalid name (digits) → 400 validation error ──────
        mockMvc.perform(patch("/api/users/" + userAId)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", "default")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"123Invalid"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }
}
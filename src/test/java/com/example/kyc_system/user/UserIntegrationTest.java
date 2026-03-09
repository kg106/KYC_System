package com.example.kyc_system.user;

import com.example.kyc_system.base.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ============================================================
 * USER CRUD — INTEGRATION TESTS
 * ============================================================
 *
 * WHAT WE'RE TESTING:
 * - GET /api/users/{id} → Get user by ID (self or admin)
 * - PATCH /api/users/{id} → Update profile (self or admin)
 * - DELETE /api/users/{id} → Delete user (admin only)
 *
 * KEY LEARNING — @PreAuthorize with SpEL:
 * Your controller
 * uses: @PreAuthorize("@securityService.canAccessUser(#userId)")
 * This means: "Call securityService.canAccessUser(userId) — if it returns
 * true, allow the request. If false, return 403 Forbidden."
 *
 * canAccessUser() returns true if:
 * - The logged-in user's ID matches the requested userId, OR
 * - The logged-in user has ROLE_ADMIN / ROLE_TENANT_ADMIN
 */
@DisplayName("User API — Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserIntegrationTest extends BaseIntegrationTest {

    private static final String TENANT_ID = "default";

    // ============================================================
    // TEST 1: User can get their own profile
    // ============================================================

    @Test
    @Order(1)
    @DisplayName("✅ User should be able to view their own profile")
    void shouldAllowUserToViewOwnProfile() throws Exception {
        // ── ARRANGE ─────────────────────────────────────────────
        String email = "selfview_" + System.currentTimeMillis() + "@test.com";
        Long uid = registerUser("Self View User", email, "SelfView@123", TENANT_ID);
        String token = loginAndGetToken(email, "SelfView@123");

        // ── ACT & ASSERT ─────────────────────────────────────────
        mockMvc.perform(
                get("/api/users/" + uid)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-ID", TENANT_ID))

                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(uid))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist()); // Security check!
    }

    // ============================================================
    // TEST 2: User CANNOT view another user's profile
    // ============================================================

    @Test
    @Order(2)
    @DisplayName("❌ User should NOT be able to view another user's profile")
    void shouldBlockUserFromViewingOtherProfile() throws Exception {
        // ── ARRANGE ─────────────────────────────────────────────
        Long user1Id = registerUser(
                "User One", "user1_" + System.currentTimeMillis() + "@test.com",
                "UserOne@123", TENANT_ID);
        String user1Token = loginAndGetToken(
                "user1_" + System.currentTimeMillis() + "@test.com", "UserOne@123");

        // Create user2 — user1 will try to view this profile
        String user2Email = "user2_" + System.currentTimeMillis() + "@test.com";
        Long user2Id = registerUser("User Two", user2Email, "UserTwo@123", TENANT_ID);

        // Hmm, we need user1's token. Let me redo this properly:
        // Re-register user1 with a known email
        String u1email = "u1_" + System.nanoTime() + "@test.com";
        Long u1id = registerUser("User One", u1email, "UserOne@123", TENANT_ID);
        String u1token = loginAndGetToken(u1email, "UserOne@123");

        String u2email = "u2_" + System.nanoTime() + "@test.com";
        Long u2id = registerUser("User Two", u2email, "UserTwo@123", TENANT_ID);

        // ── ACT: User 1 tries to access User 2's profile ─────────
        mockMvc.perform(
                get("/api/users/" + u2id) // ← User 2's ID
                        .header("Authorization", "Bearer " + u1token) // ← User 1's token
                        .header("X-Tenant-ID", TENANT_ID))

                // securityService.canAccessUser(u2id) → u1's JWT userId ≠ u2id → false
                // AND u1 is not ADMIN → 403 Forbidden
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // TEST 3: User can update their own profile
    // ============================================================

    @Test
    @Order(3)
    @DisplayName("✅ User should be able to update their own profile (name, mobile)")
    void shouldAllowUserToUpdateOwnProfile() throws Exception {
        // ── ARRANGE ─────────────────────────────────────────────
        String email = "updateme_" + System.currentTimeMillis() + "@test.com";
        Long uid = registerUser("Original Name", email, "Update@123", TENANT_ID);
        String token = loginAndGetToken(email, "Update@123");

        // UserUpdateDTO fields (all optional — partial update)
        String updateJson = """
                {
                    "name": "Updated Name",
                    "mobileNumber": "9999999999"
                }
                """;

        // ── ACT & ASSERT ─────────────────────────────────────────
        mockMvc.perform(
                patch("/api/users/" + uid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-ID", TENANT_ID)
                        .content(updateJson))

                .andExpect(status().isOk())
                // The response should reflect the updated name
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.mobileNumber").value("9999999999"))
                // Email should remain unchanged
                .andExpect(jsonPath("$.email").value(email));
    }

    // ============================================================
    // TEST 4: Admin can delete a user
    // ============================================================

    @Test
    @Order(4)
    @DisplayName("✅ Admin should be able to delete any user")
    void shouldAllowAdminToDeleteUser() throws Exception {
        // ── ARRANGE ─────────────────────────────────────────────
        // Create a user to be deleted
        String victimEmail = "tobedeleted_" + System.currentTimeMillis() + "@test.com";
        Long victimId = registerUser("To Be Deleted", victimEmail, "Delete@123", TENANT_ID);

        // Admin logs in (seeded by DataInitializer)
        String adminToken = loginAndGetToken("admin@kyc.com", "Password@123");

        // ── ACT: Admin deletes the user ───────────────────────────
        mockMvc.perform(
                delete("/api/users/" + victimId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string("User deleted successfully"));

        // ── ASSERT: User no longer exists ────────────────────────
        // Trying to get the deleted user → 404
        mockMvc.perform(
                get("/api/users/" + victimId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isNotFound()); // User is gone!
    }

    // ============================================================
    // TEST 5: Regular user CANNOT delete another user
    // ============================================================

    @Test
    @Order(5)
    @DisplayName("❌ Regular user should NOT be able to delete another user")
    void shouldBlockRegularUserFromDeletingOthers() throws Exception {
        String attackerEmail = "attacker_" + System.currentTimeMillis() + "@test.com";
        registerUser("Attacker", attackerEmail, "Attack@123", TENANT_ID);
        String attackerToken = loginAndGetToken(attackerEmail, "Attack@123");

        String victimEmail = "victim2_" + System.currentTimeMillis() + "@test.com";
        Long victimId = registerUser("Victim", victimEmail, "Victim@123", TENANT_ID);

        // Attacker tries to delete victim
        mockMvc.perform(
                delete("/api/users/" + victimId)
                        .header("Authorization", "Bearer " + attackerToken)
                        .header("X-Tenant-ID", TENANT_ID))

                // @PreAuthorize("hasRole('ADMIN')") — attacker has ROLE_USER → 403
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // TEST 6: Admin search returns paginated results
    // ============================================================

    @Test
    @Order(6)
    @DisplayName("✅ Admin can search users with pagination")
    void shouldReturnPaginatedUsersForAdmin() throws Exception {
        String adminToken = loginAndGetToken("admin@kyc.com", "Password@123");

        // Create a few users so there's data to paginate
        for (int i = 0; i < 3; i++) {
            registerUser("Search User " + i,
                    "searchuser" + i + "_" + System.nanoTime() + "@test.com",
                    "Search@123", TENANT_ID);
        }

        // Search endpoint with Spring Data Pageable parameters
        mockMvc.perform(
                get("/api/users/search")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isOk())
                // Spring Page response has "content" array and "totalElements"
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }
}
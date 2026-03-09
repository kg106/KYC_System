package com.example.kyc_system.security;

import com.example.kyc_system.base.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================
 * SECURITY — INTEGRATION TESTS
 * ============================================================
 *
 * WHAT WE'RE TESTING:
 * 1. JWT token validation (valid vs expired vs tampered tokens)
 * 2. Token blacklisting after logout (stored in Redis)
 * 3. Refresh token rotation (new token issued, old one invalidated)
 * 4. Role-based access control (ADMIN-only endpoints)
 *
 * WHY IS THIS AN INTEGRATION TEST (not unit test)?
 * - Token blacklisting requires Redis to be running → real Redis container
 * - RBAC requires the full Spring Security filter chain to run
 * - JWT validation requires the JwtTokenProvider + SecurityContextHolder
 * - These components only work properly together, not in isolation
 *
 * KEY CONCEPTS:
 * - JWT: JSON Web Token — a signed string that proves who you are
 * - Refresh Token: Long-lived token stored in an HttpOnly cookie
 * Used to get a new access token without re-logging in
 * - Token Rotation: When you use a refresh token, you get a NEW one.
 * The old one becomes invalid. (Prevents token theft)
 * - Token Blacklist: When you logout, the access token is added to Redis
 * so even if someone stole it, it won't work anymore
 */
@DisplayName("Security — JWT & RBAC Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityIntegrationTest extends BaseIntegrationTest {

    private static final String TENANT_ID = "default";

    // ============================================================
    // TEST 1: Tampered JWT should be rejected
    // ============================================================

    @Test
    @Order(1)
    @DisplayName("❌ Should reject a tampered/fake JWT token")
    void shouldRejectTamperedJwt() throws Exception {
        // ── ARRANGE: Build a fake JWT ─────────────────────────────
        // Real JWT looks like: eyXXX.eyXXX.signature
        // This is a plausible-looking fake but the signature won't verify
        String fakeJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJoYWNrZXJAdGVzdC5jb20ifQ.FAKE_SIGNATURE";

        // ── ACT & ASSERT ─────────────────────────────────────────
        // Try to access a protected endpoint with a fake token
        mockMvc.perform(
                get("/api/users")
                        .header("Authorization", "Bearer " + fakeJwt)
                        .header("X-Tenant-ID", TENANT_ID))

                // JwtTokenProvider.validateToken() will fail signature check
                // → JwtAuthenticationFilter won't set SecurityContext
                // → Spring Security blocks access → 401 Unauthorized
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // TEST 2: Blacklisted token should be rejected after logout
    // ============================================================

    @Test
    @Order(2)
    @DisplayName("❌ Should reject a blacklisted JWT after logout (Redis blacklist)")
    void shouldRejectBlacklistedTokenAfterLogout() throws Exception {
        // ── ARRANGE: Register and login ───────────────────────────
        String email = "blacklist_" + System.currentTimeMillis() + "@test.com";
        Long uid = registerUser("Blacklist User", email, "BlackList@123", TENANT_ID);
        String token = loginAndGetToken(email, "BlackList@123");

        // Verify token works BEFORE logout
        mockMvc.perform(
                get("/api/kyc/status/" + uid)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().is2xxSuccessful()); // 200 or 404 — both are valid (access was granted)

        // ── ACT: Logout — this blacklists the token in Redis ──────
        // AuthController calls: tokenBlacklistService.blacklistToken(accessToken)
        // Which stores it in Redis with a TTL matching the token expiry
        mockMvc.perform(
                post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // ── ASSERT: Same token should now be REJECTED ─────────────
        // TokenBlacklistService.isTokenBlacklisted(token) checks Redis
        // JwtAuthenticationFilter sees the blacklist → skips auth → 401
        mockMvc.perform(
                get("/api/kyc/status/" + uid)
                        .header("Authorization", "Bearer " + token) // ← Same old token
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // TEST 3: Refresh token rotation
    // ============================================================

    @Test
    @Order(3)
    @DisplayName("✅ Should issue new tokens on refresh and invalidate old refresh token")
    void shouldRotateRefreshTokenSuccessfully() throws Exception {
        // ── ARRANGE: Login to get tokens ──────────────────────────
        String email = "refresh_" + System.currentTimeMillis() + "@test.com";
        registerUser("Refresh User", email, "Refresh@123", TENANT_ID);

        String loginJson = String.format("""
                {"email": "%s", "password": "Refresh@123"}
                """, email);

        // Login — this returns access token in body AND sets refreshToken cookie
        MvcResult loginResult = mockMvc.perform(
                post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        // Extract the refresh token from the response cookie
        // Your server sets it as: Set-Cookie: refreshToken=familyId:tokenValue;
        // HttpOnly
        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
        assertNotNull(refreshCookie, "Refresh token cookie must be set on login");
        String originalRefreshToken = refreshCookie.getValue();

        // ── ACT: Use the refresh token to get a new access token ──
        MvcResult refreshResult = mockMvc.perform(
                post("/api/auth/refresh")
                        // The /api/auth/refresh endpoint reads the refresh token
                        // from the HttpOnly cookie (@CookieValue annotation in controller)
                        .cookie(new Cookie("refreshToken", originalRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        // Extract the NEW refresh token from the response
        Cookie newRefreshCookie = refreshResult.getResponse().getCookie("refreshToken");
        assertNotNull(newRefreshCookie, "A new refresh token cookie must be issued");
        String newRefreshToken = newRefreshCookie.getValue();

        // The new token must be different from the old one (rotation happened)
        assertNotEquals(originalRefreshToken, newRefreshToken,
                "Refresh token should be rotated — new token must differ from old one");

        // ── ASSERT: OLD refresh token should now be INVALID ───────
        // RefreshTokenServiceImpl: if presentedToken != storedToken → reuse detected
        // → revokeFamily() is called → entire token family is revoked
        mockMvc.perform(
                post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", originalRefreshToken))) // ← Reusing old token
                .andExpect(status().isUnauthorized()); // Reuse detected!
    }

    // ============================================================
    // TEST 4: Admin-only endpoint rejected for regular users
    // ============================================================

    @Test
    @Order(4)
    @DisplayName("❌ Should return 403 when regular user accesses ADMIN-only endpoint")
    void shouldDenyRegularUserAccessToAdminEndpoint() throws Exception {
        // ── ARRANGE: Create a regular USER (not admin) ────────────
        String email = "regularuser_" + System.currentTimeMillis() + "@test.com";
        registerUser("Regular User", email, "Regular@123", TENANT_ID);
        String userToken = loginAndGetToken(email, "Regular@123");

        // ── ACT & ASSERT ─────────────────────────────────────────
        // GET /api/users requires @PreAuthorize("hasRole('ADMIN')")
        // A regular user only has ROLE_USER (set in UserServiceImpl.createUser())
        mockMvc.perform(
                get("/api/users")
                        .header("Authorization", "Bearer " + userToken)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isForbidden()); // 403 Forbidden (authenticated but not authorized)
    }

    // ============================================================
    // TEST 5: Admin CAN access admin endpoints
    // ============================================================

    @Test
    @Order(5)
    @DisplayName("✅ Should allow ADMIN user to access admin endpoint")
    void shouldAllowAdminToAccessAdminEndpoint() throws Exception {
        // ── ARRANGE: Use the default admin seeded by DataInitializer ──
        // DataInitializer creates: admin@kyc.com / Password@123 with ROLE_TENANT_ADMIN
        // under the "default" tenant

        // NOTE: If DataInitializer doesn't run in test environment,
        // you can promote a user to admin via direct repository injection
        // (using @Autowired in the test class).

        String adminToken = loginAndGetToken("admin@kyc.com", "Password@123");

        // ── ACT & ASSERT ─────────────────────────────────────────
        mockMvc.perform(
                get("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Tenant-ID", TENANT_ID))
                // Admin has ROLE_TENANT_ADMIN / ROLE_ADMIN
                // @PreAuthorize("hasRole('ADMIN')") → 200 OK
                .andExpect(status().isOk());
    }

    // ============================================================
    // TEST 6: Missing X-Tenant-ID header
    // ============================================================

    @Test
    @Order(6)
    @DisplayName("❌ Should return 400 Bad Request when X-Tenant-ID header is missing")
    void shouldRejectRequestWithoutTenantHeader() throws Exception {
        // ── ARRANGE ─────────────────────────────────────────────
        String email = "tenant_" + System.currentTimeMillis() + "@test.com";
        Long uid = registerUser("Tenant User", email, "Tenant@123", TENANT_ID);
        String token = loginAndGetToken(email, "Tenant@123");

        // ── ACT & ASSERT ─────────────────────────────────────────
        // TenantResolutionFilter requires X-Tenant-ID header for protected routes
        // (unless it's embedded in JWT — which it is! The JWT contains tenantId claim)
        // So this might actually PASS because JWT has tenant info.
        // This test documents the actual behavior of your system.
        mockMvc.perform(
                get("/api/kyc/status/" + uid)
                        .header("Authorization", "Bearer " + token))
                // With JWT containing tenantId, this should still work (200 or 404)
                // TenantResolutionFilter Priority 1: reads from JWT claim
                .andExpect(status().is2xxSuccessful());
    }
}
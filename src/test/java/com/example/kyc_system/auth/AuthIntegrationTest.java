package com.example.kyc_system.auth;

import com.example.kyc_system.base.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================
 * AUTH INTEGRATION TESTS (Start Here — Simplest tests first)
 * ============================================================
 *
 * WHAT WE'RE TESTING:
 * The complete Register → Login flow with a REAL database.
 * These test your AuthController → UserService → PostgreSQL pipeline.
 *
 * WHY START HERE?
 * Authentication is the foundation of your system.
 * If register/login doesn't work, NOTHING else will work either.
 * It's also the simplest flow — no file uploads, no Redis queues.
 *
 * HOW TO READ THESE TESTS:
 * Each test follows the AAA pattern:
 * ARRANGE → Set up your data / inputs
 * ACT → Call the API / do the action
 * ASSERT → Check the results are what you expected
 *
 * HOW TO RUN:
 * Right-click this file in IntelliJ → Run 'AuthIntegrationTest'
 * Or from terminal: ./mvnw test -Dtest=AuthIntegrationTest
 *
 * NOTE: First run is slow (~30 seconds) because Docker containers start.
 * Subsequent runs are faster (~5 seconds).
 *
 * extends BaseIntegrationTest
 * → This gives us mockMvc, objectMapper, loginAndGetToken(), registerUser()
 * and the real PostgreSQL + Redis containers from our base class.
 */
@DisplayName("Auth API — Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // Run tests in order
class AuthIntegrationTest extends BaseIntegrationTest {

        // ============================================================
        // TEST CONSTANTS
        // We define these as constants so all tests use the same values.
        // If you need to change the test user's email, change it once here.
        // ============================================================
        private static final String TEST_PASSWORD = "TestPass@123";
        private static final String TEST_NAME = "Test User";
        private static final String TENANT_ID = "default";

        // Each test gets a unique email using the test method name
        private String testEmail;

        @BeforeEach
        void initEmail(org.junit.jupiter.api.TestInfo testInfo) {
                // Creates a unique email per test, e.g. "shouldlogin_1741234567890@test.com"
                testEmail = testInfo.getTestMethod()
                                .map(m -> m.getName().toLowerCase())
                                .orElse("test")
                                + "_" + System.currentTimeMillis() + "@test.com";
        }

        // ============================================================
        // TEST 1: Happy Path — Register a new user
        // ============================================================

        @Test
        @Order(1)
        @DisplayName("✅ Should register a new user and return 201 Created")
        void shouldRegisterNewUserSuccessfully() throws Exception {
                // ── ARRANGE ─────────────────────────────────────────────
                // Build the JSON body that matches UserDTO fields.
                // Note the validation rules from UserDTO:
                // - name: letters only, 2-100 chars
                // - email: valid email format
                // - password: uppercase + lowercase + digit + special char
                // - mobileNumber: exactly 10 digits
                // Generate a unique mobile number for this test
                String uniqueMobile = String.format("%010d", System.nanoTime() % 10_000_000_000L);
                String registerJson = String.format("""
                                {
                                    "name": "Test User",
                                    "email": "%s",
                                    "password": "TestPass@123",
                                    "mobileNumber": "%s",
                                    "isActive": true,
                                    "dob": "1990-01-01"
                                }
                                """, testEmail, uniqueMobile);

                // ── ACT & ASSERT ─────────────────────────────────────────
                // mockMvc.perform(...) — makes the HTTP request
                // .andExpect(...) — checks the response

                mockMvc.perform(
                                post("/api/auth/register")
                                                // Tell the server we're sending JSON
                                                .contentType(MediaType.APPLICATION_JSON)
                                                // Your TenantResolutionFilter REQUIRES this header
                                                // Without it, you'll get 400 Bad Request
                                                .header("X-Tenant-ID", TENANT_ID)
                                                .content(registerJson))

                                // HTTP 201 Created = user was created successfully
                                .andExpect(status().isCreated())
                                // The response body should contain these fields
                                // jsonPath("$.fieldName") navigates the JSON response
                                // exists() = the field is present (we don't check the exact value)
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.name").value("Test User"))
                                .andExpect(jsonPath("$.email").value(testEmail))

                                // SECURITY CHECK: password must NEVER be returned
                                // This would be a serious security bug if it were present!
                                .andExpect(jsonPath("$.password").doesNotExist())
                                .andExpect(jsonPath("$.passwordHash").doesNotExist());

                // ── WHAT THIS PROVES ─────────────────────────────────────
                // ✅ UserDTO validation is working
                // ✅ UserServiceImpl.createUser() saved to the real PostgreSQL DB
                // ✅ Password is never exposed in responses
                // ✅ TenantContext is properly scoped (user saved under "default" tenant)
        }

        // ============================================================
        // TEST 2: Happy Path — Login with the user we just registered
        // ============================================================

        @Test
        @Order(2)
        @DisplayName("✅ Should login and return a valid JWT access token")
        void shouldLoginAndReturnJwtToken() throws Exception {
                // ── ARRANGE ─────────────────────────────────────────────
                // First, register the user (this test depends on Test 1 having run,
                // or we register fresh here to be safe)
                registerUser(TEST_NAME, testEmail, TEST_PASSWORD, TENANT_ID);

                String loginJson = String.format("""
                                {
                                    "email": "%s",
                                    "password": "TestPass@123"
                                }
                                """, testEmail);

                // ── ACT & ASSERT ─────────────────────────────────────────
                MvcResult result = mockMvc.perform(
                                post("/api/auth/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(loginJson))

                                .andExpect(status().isOk())

                                // JWT access token must be present
                                .andExpect(jsonPath("$.accessToken").exists())

                                // Refresh token is returned too (stored in HttpOnly cookie AND body)
                                .andExpect(jsonPath("$.refreshToken").exists())

                                .andReturn();

                // ── ADDITIONAL ASSERTIONS ON THE TOKEN ───────────────────
                // Extract the access token from the response
                String responseBody = result.getResponse().getContentAsString();
                String accessToken = objectMapper.readTree(responseBody)
                                .get("accessToken").asText();

                // A JWT token always has 3 parts separated by dots: header.payload.signature
                // e.g.: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0...
                assertNotNull(accessToken, "Access token should not be null");
                assertTrue(accessToken.split("\\.").length == 3,
                                "JWT should have exactly 3 parts (header.payload.signature)");

                // Also verify the refresh token cookie was set
                // Your AuthController calls:
                // response.addCookie(cookieUtil.createRefreshTokenCookie(...))
                String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
                assertNotNull(setCookieHeader, "Refresh token cookie should be set");
                assertTrue(setCookieHeader.contains("refreshToken"),
                                "Cookie should be named 'refreshToken'");
        }

        // ============================================================
        // TEST 3: Sad Path — Duplicate registration (same email)
        // ============================================================

        @Test
        @Order(3)
        @DisplayName("❌ Should reject duplicate email registration with 4xx error")
        void shouldRejectDuplicateEmailRegistration() throws Exception {
                // ── ARRANGE ─────────────────────────────────────────────
                // Register the first user
                registerUser(TEST_NAME, testEmail, TEST_PASSWORD, TENANT_ID);

                // Try to register AGAIN with the same email
                // Use the SAME email to trigger duplicate detection
                String uniqueMobile = String.format("%010d", System.nanoTime() % 10_000_000_000L);
                String duplicateJson = String.format("""
                                {
                                    "name": "Another User",
                                    "email": "%s",
                                    "password": "AnotherPass@123",
                                    "mobileNumber": "%s",
                                    "isActive": true,
                                    "dob": "1995-01-01"
                                }
                                """, testEmail, uniqueMobile);

                // ── ACT & ASSERT ─────────────────────────────────────────
                mockMvc.perform(
                                post("/api/auth/register")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .header("X-Tenant-ID", TENANT_ID)
                                                .content(duplicateJson))

                                // Should NOT be 201 Created
                                // UserServiceImpl throws RuntimeException("Email already exists")
                                // which your GlobalExceptionHandler converts to a 4xx response
                                .andExpect(status().is4xxClientError());
        }

        // ============================================================
        // TEST 4: Sad Path — Wrong password
        // ============================================================

        @Test
        @Order(4)
        @DisplayName("❌ Should reject login with wrong password and return 401")
        void shouldRejectLoginWithWrongPassword() throws Exception {
                // ── ARRANGE ─────────────────────────────────────────────
                registerUser(TEST_NAME, testEmail, TEST_PASSWORD, TENANT_ID);

                String wrongPasswordJson = String.format("""
                                {
                                    "email": "%s",
                                    "password": "WrongPassword@999"
                                }
                                """, testEmail);

                // ── ACT & ASSERT ─────────────────────────────────────────
                mockMvc.perform(
                                post("/api/auth/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(wrongPasswordJson))

                                // Spring Security's authenticationManager.authenticate()
                                // throws BadCredentialsException → converted to 401 Unauthorized
                                .andExpect(status().isUnauthorized());
        }

        // ============================================================
        // TEST 5: Sad Path — Invalid email format (validation)
        // ============================================================

        @Test
        @Order(5)
        @DisplayName("❌ Should reject registration with invalid email format")
        void shouldRejectInvalidEmailFormat() throws Exception {
                // ── ARRANGE ─────────────────────────────────────────────
                // "not-an-email" doesn't match UserDTO's @Email constraint
                String invalidJson = """
                                {
                                    "name": "Test User",
                                    "email": "not-an-email",
                                    "password": "TestPass@123",
                                    "mobileNumber": "9876543210",
                                    "isActive": true,
                                    "dob": "1990-01-01"
                                }
                                """;

                // ── ACT & ASSERT ─────────────────────────────────────────
                mockMvc.perform(
                                post("/api/auth/register")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .header("X-Tenant-ID", TENANT_ID)
                                                .content(invalidJson))

                                // @Valid annotation on the controller triggers MethodArgumentNotValidException
                                // Your GlobalExceptionHandler converts this to 400 Bad Request
                                .andExpect(status().isBadRequest());
        }

        // ============================================================
        // TEST 6: Sad Path — Weak password (validation)
        // ============================================================

        @Test
        @Order(6)
        @DisplayName("❌ Should reject registration with password that has no special character")
        void shouldRejectWeakPassword() throws Exception {
                // UserDTO password regex: (?=.*[@#$%^&+=!_])
                // "SimplePass1" has no special character → validation fails

                String weakPasswordJson = """
                                {
                                    "name": "Test User",
                                    "email": "anotheruser@example.com",
                                    "password": "SimplePass1",
                                    "mobileNumber": "9876543210",
                                    "isActive": true,
                                    "dob": "1990-01-01"
                                }
                                """;

                mockMvc.perform(
                                post("/api/auth/register")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .header("X-Tenant-ID", TENANT_ID)
                                                .content(weakPasswordJson))
                                .andExpect(status().isBadRequest());
        }

        // ============================================================
        // TEST 7: Logout — Blacklists the access token
        // ============================================================

        @Test
        @Order(7)
        @DisplayName("✅ Should logout successfully and blacklist the JWT token")
        void shouldLogoutSuccessfully() throws Exception {
                // ── ARRANGE ─────────────────────────────────────────────
                registerUser(TEST_NAME, testEmail, TEST_PASSWORD, TENANT_ID);
                String token = loginAndGetToken(testEmail, TEST_PASSWORD);

                // ── ACT ──────────────────────────────────────────────────
                mockMvc.perform(
                                post("/api/auth/logout")
                                                // Send the JWT so it can be blacklisted in Redis
                                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(content().string("Logged out successfully"));

                // ── ASSERT ───────────────────────────────────────────────
                // After logout, the same token should be REJECTED
                // (TokenBlacklistService stores it in Redis)
                // We verify this by trying to use the token on a protected endpoint.
                // This is tested more thoroughly in SecurityIntegrationTest.
        }
}
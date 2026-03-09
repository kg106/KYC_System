package com.example.kyc_system.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ============================================================
 * BASE INTEGRATION TEST CLASS
 * ============================================================
 *
 * WHAT IS THIS CLASS?
 * This is the "foundation" that ALL other integration tests will extend.
 * Think of it like a template — it sets up the real database, real Redis,
 * and common helpers that every test needs.
 *
 * WHY DO WE NEED IT?
 * Every integration test needs:
 * 1. A real PostgreSQL database (not a fake/mock one)
 * 2. A real Redis instance (for JWT refresh tokens)
 * 3. A running Spring Boot application
 * 4. A way to make HTTP requests
 *
 * Instead of setting this up in EVERY test class, we do it once here.
 *
 * ============================================================
 * KEY ANNOTATIONS EXPLAINED
 * ============================================================
 *
 * @SpringBootTest
 *                 → Starts your ENTIRE Spring Boot application for testing.
 *                 → webEnvironment = RANDOM_PORT means it picks a free port
 *                 automatically.
 *                 → Without this, Spring doesn't start and you can't test
 *                 anything.
 *
 * @AutoConfigureMockMvc
 *                       → Gives us MockMvc — a tool to simulate HTTP requests
 *                       (like Postman, but in code).
 *                       → We use this to call POST /api/auth/login, GET
 *                       /api/kyc/status, etc.
 *
 * @Testcontainers
 *                 → Tells JUnit: "This class uses Docker containers for
 *                 testing."
 *                 → It automatically starts and stops Docker containers
 *                 before/after tests.
 *
 *                 @ActiveProfiles("test")
 *                 → Activates application-test.properties instead of
 *                 application.properties.
 *                 → This lets us use different config for tests (e.g., disable
 *                 email sending).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

        // ============================================================
        // REAL DATABASE SETUP (using Testcontainers)
        // ============================================================

        /**
         * @Container
         *            → Tells Testcontainers to manage this Docker container's
         *            lifecycle.
         *            → It starts BEFORE your tests run and stops AFTER all tests
         *            finish.
         *
         *            static
         *            → The container is shared across ALL test methods in a class
         *            (faster).
         *            → If not static, a new container starts for EACH test (very slow).
         *
         *            PostgreSQLContainer
         *            → Spins up a real PostgreSQL database inside Docker.
         *            → "postgres:15" is the Docker image version to use.
         *            → Your Flyway migrations will run automatically on this test DB.
         */
        @Container
        static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
                        .withDatabaseName("kyc_test_db")
                        .withUsername("test_user")
                        .withPassword("test_password");

        /**
         * RedisContainer
         * → Spins up a real Redis instance inside Docker.
         * → Used for JWT refresh tokens and token blacklisting.
         * → "redis:7-alpine" is a lightweight Redis Docker image.
         */
        @Container
        static RedisContainer redis = new RedisContainer("redis:7-alpine");

        // ============================================================
        // DYNAMIC PROPERTY INJECTION
        // ============================================================

        /**
         * @DynamicPropertySource
         *                        The Problem: Testcontainers assigns RANDOM ports to
         *                        the DB and Redis
         *                        (e.g., DB might be on port 54321, Redis on port
         *                        63791).
         *
         *                        We don't know these ports in advance, so we CAN'T
         *                        hardcode them
         *                        in application-test.properties.
         *
         *                        The Solution: This method runs BEFORE Spring starts,
         *                        reads the
         *                        actual ports from the running containers, and injects
         *                        them into
         *                        Spring's configuration dynamically.
         *
         *                        Result: Spring connects to the right database and
         *                        Redis automatically.
         */
        @DynamicPropertySource
        static void configureProperties(DynamicPropertyRegistry registry) {
                // Tell Spring Boot: "Use this Docker container as your database"
                registry.add("spring.datasource.url", postgres::getJdbcUrl);
                registry.add("spring.datasource.username", postgres::getUsername);
                registry.add("spring.datasource.password", postgres::getPassword);

                // Tell Spring Boot: "Use this Docker container as your Redis"
                registry.add("spring.data.redis.host", redis::getHost);
                registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        }

        // ============================================================
        // TOOLS AVAILABLE IN EVERY TEST
        // ============================================================

        /**
         * MockMvc — Your HTTP request simulator.
         *
         * Instead of running a real browser or Postman, MockMvc lets you
         * make HTTP requests directly in Java code:
         *
         * mockMvc.perform(post("/api/auth/login").content(...))
         * .andExpect(status().isOk())
         * .andExpect(jsonPath("$.accessToken").exists());
         *
         * @Autowired = Spring will inject (provide) this for you automatically.
         */
        @Autowired
        protected MockMvc mockMvc;

        /**
         * ObjectMapper — Converts between Java objects and JSON strings.
         *
         * Example:
         * UserDTO user = new UserDTO("John", "john@test.com", ...);
         * String json = objectMapper.writeValueAsString(user);
         * // json = {"name":"John","email":"john@test.com",...}
         *
         * Also works the other way:
         * UserDTO parsed = objectMapper.readValue(jsonString, UserDTO.class);
         */
        @Autowired
        protected ObjectMapper objectMapper;

        // ============================================================
        // HELPER METHODS (used in every test)
        // ============================================================

        /**
         * WHY THIS METHOD?
         * Almost every protected endpoint needs a valid JWT token.
         * Instead of copy-pasting the login code in every test,
         * we put it here once and reuse it everywhere.
         *
         * HOW IT WORKS:
         * 1. Sends POST /api/auth/login with email + password
         * 2. Reads the JWT token from the response
         * 3. Returns it as a String so you can use it in test requests
         *
         * USAGE IN TESTS:
         * String token = loginAndGetToken("user@test.com", "Password@123");
         * mockMvc.perform(get("/api/users/1")
         * .header("Authorization", "Bearer " + token))
         * .andExpect(status().isOk());
         */
        protected String loginAndGetToken(String email, String password) throws Exception {
                // Build the JSON body for the login request
                String loginJson = String.format("""
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """, email, password);

                // Perform the actual HTTP request to /api/auth/login
                // andReturn() captures the full response so we can read it
                MvcResult result = mockMvc.perform(
                                post("/api/auth/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(loginJson))
                                .andExpect(status().isOk()) // Assert login succeeded
                                .andReturn();

                // Parse the JSON response to extract the accessToken field
                // Response looks like: {"accessToken": "eyJ...", "refreshToken": "..."}
                String responseBody = result.getResponse().getContentAsString();
                return objectMapper.readTree(responseBody)
                                .get("accessToken")
                                .asText();
        }

        /**
         * WHY THIS METHOD?
         * The /api/auth/register endpoint requires X-Tenant-ID header.
         * This helper registers a user under a specific tenant and
         * returns their generated ID from the database.
         *
         * PARAMETERS:
         * 
         * @param name     - User's full name
         * @param email    - User's email (must be unique per tenant)
         * @param password - Must meet complexity rules: uppercase, lowercase, digit,
         *                 special char
         * @param tenantId - Which tenant this user belongs to (e.g., "default")
         *
         * @return the user's database ID (Long), useful for subsequent API calls
         */
        private static final java.util.concurrent.atomic.AtomicLong MOBILE_COUNTER = new java.util.concurrent.atomic.AtomicLong(
                        System.currentTimeMillis() % 10_000_000_000L);

        protected Long registerUser(String name, String email, String password, String tenantId) throws Exception {
                // Generate a unique 10-digit mobile number for each registration
                String uniqueMobile = String.format("%010d", MOBILE_COUNTER.getAndIncrement() % 10_000_000_000L);

                String body = String.format("""
                                {
                                    "name": "%s",
                                    "email": "%s",
                                    "password": "%s",
                                    "mobileNumber": "%s",
                                    "isActive": true,
                                    "dob": "1990-01-01"
                                }
                                """, name, email, password, uniqueMobile);

                MvcResult result = mockMvc.perform(
                                post("/api/auth/register")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .header("X-Tenant-ID", tenantId)
                                                .content(body))
                                .andReturn(); // ← Remove .andExpect(status().isCreated()) for now

                // Print the actual response so we can see the 500 error message
                System.out.println("REGISTER RESPONSE STATUS: " +
                                result.getResponse().getStatus());
                System.out.println("REGISTER RESPONSE BODY: " +
                                result.getResponse().getContentAsString());

                // Now assert
                org.springframework.test.util.AssertionErrors.assertEquals(
                                "Register should return 201", 201,
                                result.getResponse().getStatus());

                String responseBody = result.getResponse().getContentAsString();
                return objectMapper.readTree(responseBody).get("id").asLong();
        }

        /**
         * Resets any per-test state if needed.
         * Add @Transactional to individual tests or use @Sql to reset DB state.
         *
         * For now this is empty — we'll populate it as needed.
         * Tip: If tests start interfering with each other, you can truncate
         * tables here using JdbcTemplate or @Sql annotations.
         */
        @BeforeEach
        void setUp() {
                // Base setup — individual test classes can override this
        }
}
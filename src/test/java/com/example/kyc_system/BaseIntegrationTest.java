package com.example.kyc_system; // keep root package since your test files import from here

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
// import org.junit.jupiter.api.BeforeEach;
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
// import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// import org.junit.jupiter.api.BeforeEach;

@SuppressWarnings("resource")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

        @Container
        static final PostgreSQLContainer<?> postgreSQLContainer;

        @Container
        static final RedisContainer redisContainer;

        static {
                postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine")
                                .withDatabaseName("testdb")
                                .withUsername("testuser")
                                .withPassword("testpass");

                redisContainer = new RedisContainer("redis:7-alpine");

                postgreSQLContainer.start();
                redisContainer.start();

                // Create KYC upload directory for tests
                try {
                        Files.createDirectories(Paths.get("/tmp/kyc-test-uploads"));
                } catch (IOException e) {
                        throw new RuntimeException("Failed to create test upload directory", e);
                }

        }

        @DynamicPropertySource
        static void dynamicProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
                registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
                registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
                registry.add("spring.data.redis.host", redisContainer::getHost);
                registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        }

        @Autowired
        protected MockMvc mockMvc;
        @Autowired
        protected ObjectMapper objectMapper;

        // ✅ Fix 4: removed @BeforeEach setupDefaultTenant — DataInitializer handles
        // this

        // ── helpers ──────────────────────────────────────────────────────────────
        // Auth and registration endpoints have been moved to the centralized Auth Service.
        // Test helpers for these have been removed. Use mock JWTs or wiremock for auth in integration tests.
}
package com.example.kyc_system.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.example.kyc_system.dto.LoginDTO;
import com.example.kyc_system.dto.UserDTO;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// src/test/java/com/example/kyc_system/integration/AuthIntegrationTest.java
class AuthIntegrationTest extends BaseIntegrationTest {

    // ── TEST 1: Successful Registration ──────────────────────────────────────
    @Test
    void register_withValidData_returns201AndUserDTO() throws Exception {
        UserDTO request = UserDTO.builder()
                .name("John Doe")
                .email("john@example.com")
                .mobileNumber("9876543210")
                .password("Welcome@123")
                .dob(LocalDate.of(1990, 1, 1))
                .build();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist()); // Never expose password
    }

    // ── TEST 2: Duplicate Email Registration ─────────────────────────────────
    @Test
    void register_withDuplicateEmail_returns500() throws Exception {
        createTestUser("dup@example.com", "Existing User", "ROLE_USER");

        UserDTO request = UserDTO.builder()
                .name("Another User")
                .email("dup@example.com") // same email
                .mobileNumber("9123456780")
                .password("Welcome@123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("User email already exists"));
    }

    // ── TEST 3: Successful Login ──────────────────────────────────────────────
    @Test
    void login_withValidCredentials_returnsJwtToken() throws Exception {
        createTestUser("login@test.com", "Login User", "ROLE_USER");

        LoginDTO loginDTO = new LoginDTO("login@test.com", "Test@1234");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(cookie().exists("refreshToken"));
    }

    // ── TEST 4: Login with Wrong Password ────────────────────────────────────
    @Test
    void login_withWrongPassword_returns401() throws Exception {
        createTestUser("wrong@test.com", "User", "ROLE_USER");

        LoginDTO loginDTO = new LoginDTO("wrong@test.com", "WrongPassword@1");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isUnauthorized());
    }

    // ── TEST 5: Field Validation ──────────────────────────────────────────────
    @Test
    void register_withInvalidEmail_returns400() throws Exception {
        UserDTO request = UserDTO.builder()
                .name("Test")
                .email("not-an-email") // invalid
                .mobileNumber("9876543210")
                .password("Welcome@123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }
}
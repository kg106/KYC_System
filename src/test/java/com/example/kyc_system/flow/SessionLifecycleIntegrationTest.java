package com.example.kyc_system.flow;

import com.example.kyc_system.BaseIntegrationTest;
// import com.example.kyc_system.dto.ErrorResponse;

// import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
// import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
// import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MvcResult;
// import org.springframework.web.bind.annotation.ExceptionHandler;

// import java.time.LocalDateTime;
// import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// import org.springframework.security.authentication.BadCredentialsException;
// import org.springframework.security.core.AuthenticationException;

public class SessionLifecycleIntegrationTest extends BaseIntegrationTest {

        @Test
        void testCompleteSessionLifecycle() throws Exception {
                String uniqueId = UUID.randomUUID().toString().substring(0, 8);
                String email = "session.user." + uniqueId + "@example.com";
                String password = "Password@123";
                String mobile = String.format("7%09d", Math.abs(email.hashCode()) % 1_000_000_000L);

                // ── Step 1: Register with INVALID data — bad password, missing fields ─────
                // Bad password (no special char) → 400 from @Valid + GlobalExceptionHandler
                String badPasswordBody = """
                                {"name":"Test User","email":"%s","password":"weakpass","mobileNumber":"%s","dob":"1990-01-01"}
                                """
                                .formatted(email, mobile);

                mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(badPasswordBody))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("Validation Error"));

                // Missing name field → 400
                String missingNameBody = """
                                {"email":"%s","password":"%s","mobileNumber":"%s","dob":"1990-01-01"}
                                """.formatted(email, password, mobile);

                mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(missingNameBody))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("Validation Error"));

                // Invalid email format → 400
                String badEmailBody = """
                                {"name":"Test User","email":"not-an-email","password":"%s","mobileNumber":"%s","dob":"1990-01-01"}
                                """
                                .formatted(password, mobile);

                mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(badEmailBody))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("Validation Error"));

                // ── Step 2: Register with VALID data — succeeds ───────────────────────────
                String validBody = """
                                {"name":"Session User","email":"%s","password":"%s","mobileNumber":"%s","dob":"1990-01-01"}
                                """
                                .formatted(email, password, mobile);

                MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validBody))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.password").doesNotExist()) // password never returned
                                .andReturn();

                Long userId = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                                .get("id").asLong();

                // ── Step 3: Register SAME email again — duplicate → 500 (email exists) ────
                mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validBody))
                                .andExpect(status().is5xxServerError()); // RuntimeException("Email already exists")

                // ── Step 4: Login with WRONG password → 401 ──────────────────────────────
                String wrongPassBody = """
                                {"email":"%s","password":"WrongPass@999"}
                                """.formatted(email);

                mockMvc.perform(post("/api/auth/login")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongPassBody))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.error").value("Unauthorized"));

                // ── Step 5: Login with CORRECT credentials → get access + refresh tokens ──
                String loginBody = """
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password);

                MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").exists())
                                .andExpect(jsonPath("$.refreshToken").exists())
                                .andReturn();

                String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                                .get("accessToken").asText();
                String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                                .get("refreshToken").asText();

                // Extract the refresh token cookie as well (set as HttpOnly cookie)
                String refreshTokenCookie = loginResult.getResponse().getHeader("Set-Cookie");
                assertNotNull(refreshTokenCookie, "Refresh token cookie must be set on login");

                // ── Step 6: Access a PROTECTED endpoint WITH valid token → succeeds ───────
                mockMvc.perform(get("/api/users/" + userId)
                                .header("Authorization", "Bearer " + accessToken)
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.email").value(email));

                // ── Step 7: Access a PROTECTED endpoint WITHOUT any token → 401 ──────────
                mockMvc.perform(get("/api/users/" + userId)
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isForbidden());

                // ── Step 8: Use refresh token to get a NEW access token ──────────────────
                MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                                .header("X-Tenant-ID", "default")
                                .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").exists())
                                .andExpect(jsonPath("$.refreshToken").exists())
                                .andReturn();

                String newAccessToken = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                                .get("accessToken").asText();
                String newRefreshToken = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                                .get("refreshToken").asText();

                // New tokens must be different from original
                assertNotEquals(accessToken, newAccessToken, "Refreshed access token must be a new token");
                assertNotEquals(refreshToken, newRefreshToken, "Refresh token must rotate on each use");

                // ── Step 9: Reuse the OLD refresh token → theft detected, family revoked ──
                mockMvc.perform(post("/api/auth/refresh")
                                .header("X-Tenant-ID", "default")
                                .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
                                .andExpect(status().isUnauthorized()); // family revoked — token reuse detected

                // ── Step 10: Even the NEW refresh token is now dead (family was revoked) ──
                mockMvc.perform(post("/api/auth/refresh")
                                .header("X-Tenant-ID", "default")
                                .cookie(new jakarta.servlet.http.Cookie("refreshToken", newRefreshToken)))
                                .andExpect(status().isUnauthorized()); // entire family revoked

                // ── Step 11: Login again to get a fresh session ───────────────────────────
                MvcResult freshLoginResult = mockMvc.perform(post("/api/auth/login")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody))
                                .andExpect(status().isOk())
                                .andReturn();

                String freshAccessToken = objectMapper.readTree(freshLoginResult.getResponse().getContentAsString())
                                .get("accessToken").asText();
                String freshRefreshToken = objectMapper.readTree(freshLoginResult.getResponse().getContentAsString())
                                .get("refreshToken").asText();

                // ── Step 12: Logout — blacklists access token, revokes refresh family ─────
                mockMvc.perform(post("/api/auth/logout")
                                .header("Authorization", "Bearer " + freshAccessToken)
                                .header("X-Tenant-ID", "default")
                                .cookie(new jakarta.servlet.http.Cookie("refreshToken", freshRefreshToken)))
                                .andExpect(status().isOk())
                                .andExpect(content().string("Logged out successfully"));

                // ── Step 13: Use BLACKLISTED access token after logout → rejected ─────────
                mockMvc.perform(get("/api/users/" + userId)
                                .header("Authorization", "Bearer " + freshAccessToken)
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isForbidden()); // JwtAuthenticationFilter: token is blacklisted

                // ── Step 14: Use revoked refresh token after logout → rejected ────────────
                mockMvc.perform(post("/api/auth/refresh")
                                .header("X-Tenant-ID", "default")
                                .cookie(new jakarta.servlet.http.Cookie("refreshToken", freshRefreshToken)))
                                .andExpect(status().isUnauthorized()); // family was revoked on logout

                // ── Step 15: Refresh with NO cookie at all → 401 ─────────────────────────
                mockMvc.perform(post("/api/auth/refresh")
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isUnauthorized());

                // ── Step 16: Regular user tries an ADMIN-only endpoint → 403 ─────────────
                // Login again to get a clean token
                MvcResult loginAgain = mockMvc.perform(post("/api/auth/login")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody))
                                .andExpect(status().isOk())
                                .andReturn();

                String userToken = objectMapper.readTree(loginAgain.getResponse().getContentAsString())
                                .get("accessToken").asText();

                // DELETE /api/users/{id} requires ROLE_ADMIN — regular user gets 403
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/users/" + userId)
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isForbidden());

                // GET /api/users (list all) requires ROLE_ADMIN — regular user gets 403
                mockMvc.perform(get("/api/users")
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isForbidden());
        }
}
package com.example.kyc_system.flow;

import com.example.kyc_system.BaseIntegrationTest;
import com.example.kyc_system.service.impl.PasswordResetServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// import org.springframework.aop.framework.AopProxyUtils;
// import org.springframework.aop.support.AopUtils;
import org.springframework.test.util.AopTestUtils;

public class PasswordRecoveryIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private PasswordResetServiceImpl passwordResetService;

        /**
         * Reads the reset token directly from the in-memory tokenStorage map via
         * reflection.
         */
        /** Unwraps Spring proxy, then reads tokenStorage via reflection. */
        @SuppressWarnings("unchecked")
        private String extractToken(String email) throws Exception {
                // Unwrap CGLIB/JDK proxy to get the real PasswordResetServiceImpl instance
                PasswordResetServiceImpl realService = AopTestUtils.getTargetObject(passwordResetService);

                Field field = PasswordResetServiceImpl.class.getDeclaredField("tokenStorage");
                field.setAccessible(true);
                Map<String, Object> tokenStorage = (Map<String, Object>) field.get(realService);

                Object tokenInfo = tokenStorage.get(email);
                assertNotNull(tokenInfo, "No token found in storage for: " + email);

                Field tokenField = tokenInfo.getClass().getDeclaredField("token");
                tokenField.setAccessible(true);
                return (String) tokenField.get(tokenInfo);
        }

        @Test
        void testPasswordRecoveryJourney() throws Exception {
                String uniqueId = UUID.randomUUID().toString().substring(0, 8);
                String email = "recovery.user." + uniqueId + "@example.com";
                String originalPassword = "Original@123";
                String newPassword = "NewPass@456";
                String mobile = String.format("8%09d", Math.abs(email.hashCode()) % 1_000_000_000L);

                // ── Step 1: Register and login to get a refresh token (we'll verify it's
                // revoked after password reset) ─────────────────────────────────
                String registerBody = """
                                {"name":"Recovery User","email":"%s","password":"%s","mobileNumber":"%s","dob":"1991-05-15"}
                                """
                                .formatted(email, originalPassword, mobile);

                mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody))
                                .andExpect(status().isCreated());

                // Login to establish a session — get a refresh token that should be
                // revoked after the password reset
                String loginBody = """
                                {"email":"%s","password":"%s"}
                                """.formatted(email, originalPassword);

                MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody))
                                .andExpect(status().isOk())
                                .andReturn();

                String existingRefreshToken = objectMapper
                                .readTree(loginResult.getResponse().getContentAsString())
                                .get("refreshToken").asText();

                // ── Step 2: Request reset token for a NON-EXISTENT email — still 200
                // (security: never reveal whether an account exists) ───────────
                mockMvc.perform(post("/api/auth/forgot-password")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"email":"nobody.%s@example.com"}
                                                """.formatted(uniqueId)))
                                .andExpect(status().isOk())
                                .andExpect(content().string("If account exist, then email has been sent."));

                // ── Step 3: Request reset token for the REAL account — 200 ───────────────
                mockMvc.perform(post("/api/auth/forgot-password")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"email":"%s"}
                                                """.formatted(email)))
                                .andExpect(status().isOk())
                                .andExpect(content().string("If account exist, then email has been sent."));

                // Grab the token directly from in-memory storage (bypasses email)
                String resetToken = extractToken(email);
                assertNotNull(resetToken, "Token must be stored in memory after forgot-password");
                assertEquals(6, resetToken.length(), "Token must be 6 characters");

                // ── Step 4: Try to reset with WRONG token → 500 RuntimeException ─────────
                String wrongTokenBody = """
                                {"email":"%s","token":"WRONG1","newPassword":"%s","confirmPassword":"%s"}
                                """.formatted(email, newPassword, newPassword);

                mockMvc.perform(post("/api/auth/change-password")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongTokenBody))
                                .andExpect(status().is5xxServerError())
                                .andExpect(jsonPath("$.message").value("Invalid token"));

                // ── Step 5: Try to reset with MISMATCHED passwords → 500 ─────────────────
                String mismatchBody = """
                                {"email":"%s","token":"%s","newPassword":"%s","confirmPassword":"Different@999"}
                                """.formatted(email, resetToken, newPassword);

                mockMvc.perform(post("/api/auth/change-password")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mismatchBody))
                                .andExpect(status().is5xxServerError())
                                .andExpect(jsonPath("$.message").value("Passwords do not match"));

                // ── Step 6: Reset with CORRECT token and matching passwords → 200 ─────────
                String correctResetBody = """
                                {"email":"%s","token":"%s","newPassword":"%s","confirmPassword":"%s"}
                                """.formatted(email, resetToken, newPassword, newPassword);

                MvcResult resetResult = mockMvc.perform(post("/api/auth/change-password")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(correctResetBody))
                                .andReturn();
                // .andExpect(status().isOk())
                // .andExpect(content().string("Password successfully reset"));

                System.out.println("STEP 6 STATUS : " + resetResult.getResponse().getStatus());
                System.out.println("STEP 6 BODY   : " + resetResult.getResponse().getContentAsString());

                assertEquals(200, resetResult.getResponse().getStatus());

                // ── Step 7: OLD password no longer works → 401 ───────────────────────────
                mockMvc.perform(post("/api/auth/login")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody)) // loginBody still uses originalPassword
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.error").value("Unauthorized"));

                // ── Step 8: NEW password works → 200 ─────────────────────────────────────
                mockMvc.perform(post("/api/auth/login")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"email":"%s","password":"%s"}
                                                """.formatted(email, newPassword)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").exists());

                // ── Step 9: Pre-reset refresh token is now REVOKED — all sessions killed ──
                // resetPassword() calls refreshTokenService.revokeAllForUser() internally
                mockMvc.perform(post("/api/auth/refresh")
                                .header("X-Tenant-ID", "default")
                                .cookie(new jakarta.servlet.http.Cookie("refreshToken", existingRefreshToken)))
                                .andExpect(status().isUnauthorized()); // family was wiped on reset

                // ── Step 10: Using the SAME token again after successful reset → 500 ──────
                // tokenStorage.remove(email) is called on success, so token is gone
                mockMvc.perform(post("/api/auth/change-password")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(correctResetBody))
                                .andExpect(status().is5xxServerError())
                                .andExpect(jsonPath("$.message").value("Invalid token"));

                // ── Step 11: Rate limit — 5 total real-email requests already made:
                // Step 3 = 1, so we need 4 more to reach 5, then the 6th triggers the block ──
                for (int i = 0; i < 4; i++) { // ← was 3, now 4
                        mockMvc.perform(post("/api/auth/forgot-password")
                                        .header("X-Tenant-ID", "default")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {"email":"%s"}
                                                        """.formatted(email)))
                                        .andExpect(status().isOk());
                }

                // 6th real-email attempt hits the rate limit → 500
                mockMvc.perform(post("/api/auth/forgot-password")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"email":"%s"}
                                                """.formatted(email)))
                                .andExpect(status().is5xxServerError())
                                .andExpect(jsonPath("$.message").value(
                                                "Daily limit reached. You can only attempt to reset your password 5 times a day."));
        }
}
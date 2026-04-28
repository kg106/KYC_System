package com.example.kyc_system.flow;

import com.example.kyc_system.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Journey 5 — KYC Business Rules
 *
 * Tests the core KYC submission rules from a user's perspective:
 *
 * 1. Invalid file type is rejected before any DB work
 * 2. Unknown document type enum is rejected
 * 3. A valid submission is accepted and stored encrypted at rest
 * 4. A second submission is blocked while the first is still in progress
 * 5. After a failure, a user can resubmit (attempt counter increments)
 * 6. After exhausting the daily limit, further submissions are blocked
 * 7. A regular user cannot search all KYC requests (admin-only)
 * 8. A user can only view their own KYC status, not another user's
 *
 * Daily limit semantics (maxDailyAttempts=2):
 * The system sums attemptNumber across all of today's KYC requests per user.
 * - Fresh submit: attemptNumber=1 → daily sum = 1 (allowed)
 * - Resubmit after FAIL: attemptNumber=2 → daily sum = 3 (1+2, blocked)
 * So with maxDailyAttempts=2, a user gets exactly one fresh attempt and
 * one retry before being blocked for the day.
 */
public class KycBusinessRulesIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Minimal valid 1×1 white JPEG — passes file-type validation,
    // but contains no PAN/AADHAAR text, so OCR always fails → FAILED status
    private static final byte[] MINIMAL_JPEG = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, (byte) 0xFF, (byte) 0xDB, 0x00, 0x43, 0x00, 0x08,
            0x06, 0x06, 0x07, 0x06, 0x05, 0x08, 0x07, 0x07, 0x07, 0x09, 0x09, 0x08, 0x0A, 0x0C, 0x14, 0x0D,
            0x0C, 0x0B, 0x0B, 0x0C, 0x19, 0x12, 0x13, 0x0F, 0x14, 0x1D, 0x1A, (byte) 0x1F, 0x1E, 0x1D, 0x1A,
            0x1C, 0x1C, 0x20, 0x24, 0x2E, 0x27, 0x20, 0x22, 0x2C, 0x23, 0x1C, 0x1C, 0x28, 0x37, 0x29, 0x2C,
            0x30, 0x31, 0x34, 0x34, 0x34, 0x1F, 0x27, 0x39, 0x3D, 0x38, 0x32, 0x3C, 0x2E, 0x33, 0x34, 0x32,
            (byte) 0xFF, (byte) 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x01, 0x00, 0x01, 0x01, 0x01, 0x11, 0x00,
            (byte) 0xFF, (byte) 0xC4, 0x00, 0x1F, 0x00, 0x00, 0x01, 0x05, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x09, 0x0A, 0x0B, (byte) 0xFF, (byte) 0xC4, 0x00, (byte) 0xB5, 0x10, 0x00, 0x02, 0x01, 0x03, 0x03,
            0x02, 0x04, 0x03, 0x05, 0x05, 0x04, 0x04, 0x00, 0x00, 0x01, 0x7D, 0x01, 0x02, 0x03, 0x00, 0x04,
            0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07, 0x22, 0x71, 0x14, 0x32, (byte) 0x81,
            (byte) 0x91, (byte) 0xA1, 0x08, 0x23, 0x42, (byte) 0xB1, (byte) 0xC1, 0x15, 0x52, (byte) 0xD1,
            (byte) 0xF0, 0x24, 0x33, 0x62, 0x72, (byte) 0x82, 0x09, 0x0A, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x25,
            0x26, 0x27, 0x28, 0x29, 0x2A, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x43, 0x44, 0x45, 0x46,
            0x47, 0x48, 0x49, 0x4A, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A, 0x63, 0x64, 0x65, 0x66,
            0x67, 0x68, 0x69, 0x6A, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, (byte) 0x83, (byte) 0x84,
            (byte) 0x85, (byte) 0x86, (byte) 0x87, (byte) 0x88, (byte) 0x89, (byte) 0x8A, (byte) 0x92, (byte) 0x93,
            (byte) 0x94, (byte) 0x95, (byte) 0x96, (byte) 0x97, (byte) 0x98, (byte) 0x99, (byte) 0x9A, (byte) 0xA2,
            (byte) 0xA3, (byte) 0xA4, (byte) 0xA5, (byte) 0xA6, (byte) 0xA7, (byte) 0xA8, (byte) 0xA9, (byte) 0xAA,
            (byte) 0xB2, (byte) 0xB3, (byte) 0xB4, (byte) 0xB5, (byte) 0xB6, (byte) 0xB7, (byte) 0xB8, (byte) 0xB9,
            (byte) 0xBA, (byte) 0xC2, (byte) 0xC3, (byte) 0xC4, (byte) 0xC5, (byte) 0xC6, (byte) 0xC7, (byte) 0xC8,
            (byte) 0xC9, (byte) 0xCA, (byte) 0xD2, (byte) 0xD3, (byte) 0xD4, (byte) 0xD5, (byte) 0xD6, (byte) 0xD7,
            (byte) 0xD8, (byte) 0xD9, (byte) 0xDA, (byte) 0xE1, (byte) 0xE2, (byte) 0xE3, (byte) 0xE4, (byte) 0xE5,
            (byte) 0xE6, (byte) 0xE7, (byte) 0xE8, (byte) 0xE9, (byte) 0xEA, (byte) 0xF1, (byte) 0xF2, (byte) 0xF3,
            (byte) 0xF4, (byte) 0xF5, (byte) 0xF6, (byte) 0xF7, (byte) 0xF8, (byte) 0xF9, (byte) 0xFA,
            (byte) 0xFF, (byte) 0xDA, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, (byte) 0xFB, 0x43,
            (byte) 0xFF, (byte) 0xD9
    };

    private MockMultipartFile makeJpeg() {
        return new MockMultipartFile("file", "doc.jpg", "image/jpeg", MINIMAL_JPEG);
    }

    /**
     * Submits a PAN upload for the given user and returns the requestId.
     * Expects 202 Accepted.
     */
    private Long submitPan(Long userId, String docNumber, String token, String tenantId) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/kyc/upload")
                .file(makeJpeg())
                .param("userId", String.valueOf(userId))
                .param("documentType", "PAN")
                .param("documentNumber", docNumber)
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", tenantId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").exists())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("requestId").asLong();
    }

    /**
     * Polls GET /api/kyc/status/{userId} until the latest request leaves
     * SUBMITTED/PROCESSING and reaches a terminal state (FAILED or VERIFIED).
     *
     * A blank JPEG produces null OCR extractions → name/dob/doc all mismatch
     * → verifyAndSave() sets finalStatus=FAILED. We also accept VERIFIED in case
     * future OCR behaviour changes, but assert FAILED at the call site if needed.
     *
     * Times out after 30 seconds. Tesseract may be slow in CI environments.
     */
    private String waitForTerminal(Long userId, String token, String tenantId) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult poll = mockMvc.perform(get("/api/kyc/status/" + userId)
                    .header("Authorization", "Bearer " + token)
                    .header("X-Tenant-ID", tenantId))
                    .andReturn();
            String status = objectMapper.readTree(poll.getResponse().getContentAsString())
                    .path("status").asText();
            if ("FAILED".equals(status) || "VERIFIED".equals(status)) {
                Thread.sleep(500); // ← ADD THIS: let the worker's transaction fully commit
                return status;
            }
            Thread.sleep(500);
        }
        fail("KYC request did not reach a terminal status within 30 seconds — worker may not be running");
        return null;
    }

    @Test
    void testKycBusinessRules() throws Exception {

        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String userEmail = "kyc.biz." + uniqueId + "@example.com";
        String userPass = "Password@123";
        String mobile = String.format("8%09d", Math.abs(userEmail.hashCode()) % 1_000_000_000L);
        String docNumber = "ABCDE1234F";

        // ── Setup ────────────────────────────────────────────────────────────────
        // Create a tenant with maxDailyAttempts=2:
        // user gets exactly 1 fresh attempt (attemptNumber=1)
        // and 1 retry after failure (attemptNumber=2), then blocked (sum=3 > 2).

        MvcResult superLogin = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"superadmin@kyc.com","password":"SuperAdmin@123"}
                        """))
                .andExpect(status().isOk())
                .andReturn();
        String superToken = objectMapper.readTree(superLogin.getResponse().getContentAsString())
                .get("accessToken").asText();

        String tenantId = "biz_" + uniqueId;
        mockMvc.perform(post("/api/tenants")
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "tenantId": "%s",
                          "name": "Biz Rules Tenant",
                          "email": "biz@biz.com",
                          "maxDailyAttempts": 2,
                          "allowedDocumentTypes": ["PAN", "AADHAAR"]
                        }
                        """.formatted(tenantId)))
                .andExpect(status().isCreated());

        // Register a regular user under this tenant
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Biz User","email":"%s","password":"%s",
                         "mobileNumber":"%s","dob":"1990-06-15"}
                        """.formatted(userEmail, userPass, mobile)))
                .andExpect(status().isCreated())
                .andReturn();
        Long userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asLong();

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .header("X-Tenant-ID", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(userEmail, userPass)))
                .andExpect(status().isOk())
                .andReturn();
        String userToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();

        // ── Rule 1: Invalid file type is rejected ─────────────────────────────────
        // Business: only image files are accepted for KYC documents.
        // KycFileValidator throws IllegalArgumentException → controller catches → 400
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "doc.txt", "text/plain", "not an image".getBytes());

        mockMvc.perform(multipart("/api/kyc/upload")
                .file(textFile)
                .param("userId", String.valueOf(userId))
                .param("documentType", "PAN")
                .param("documentNumber", docNumber)
                .header("Authorization", "Bearer " + userToken)
                .header("X-Tenant-ID", tenantId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // ── Rule 2: Unknown document type is rejected ─────────────────────────────
        // Business: only PAN and AADHAAR are valid document types.
        // Spring cannot bind "DRIVING_LICENSE" to DocumentType enum → 400
        mockMvc.perform(multipart("/api/kyc/upload")
                .file(makeJpeg())
                .param("userId", String.valueOf(userId))
                .param("documentType", "DRIVING_LICENSE")
                .param("documentNumber", docNumber)
                .header("Authorization", "Bearer " + userToken)
                .header("X-Tenant-ID", tenantId))
                .andExpect(status().isBadRequest());

        // ── Rule 3: Valid submission is accepted; document number encrypted at rest ─
        // Business: KYC documents must be accepted and document numbers must never
        // be stored in plain text in the database (PII protection).
        Long requestId = submitPan(userId, docNumber, userToken, tenantId);

        // Verify encryption: query the raw DB column — must not equal plain text
        String rawInDb = jdbcTemplate.queryForObject(
                "SELECT document_number FROM kyc_documents WHERE kyc_request_id = ?",
                String.class, requestId);

        assertNotNull(rawInDb, "document_number must be stored in the database");
        assertNotEquals(docNumber, rawInDb,
                "SECURITY VIOLATION: document_number stored as plain text — AES encryption not applied");
        assertFalse(rawInDb.contains(docNumber),
                "SECURITY VIOLATION: plain document number visible inside stored value");

        // ── Rule 4: Cannot submit again while request is in progress ──────────────
        // Business: only one active KYC request per document type is allowed at a time.
        // Status is SUBMITTED/PROCESSING → second upload must be rejected.
        mockMvc.perform(multipart("/api/kyc/upload")
                .file(makeJpeg())
                .param("userId", String.valueOf(userId))
                .param("documentType", "PAN")
                .param("documentNumber", docNumber)
                .header("Authorization", "Bearer " + userToken)
                .header("X-Tenant-ID", tenantId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Only one KYC request for PAN can be processed at a time."));

        // Wait for the async worker to process the request.
        // Our blank JPEG produces null extractions → name/dob/doc all mismatch →
        // FAILED.
        // Extra sleep gives the worker's transaction time to fully commit before
        // createOrReuse reads the status in its own REQUIRES_NEW transaction.
        waitForTerminal(userId, userToken, tenantId);
        Thread.sleep(2000);

        // ── Rule 5: User can resubmit after a failure (attempt counter increments) ─
        // Business: a failed KYC should not permanently block the user —
        // they must be allowed to retry within their daily limit.
        // After FAILED: createOrReuse() reuses the row and sets attemptNumber=2.
        // Daily sum = 1 (previous attemptNumber) + 1 (increment) = 2 → within limit.
        submitPan(userId, docNumber, userToken, tenantId);

        // Verify the attempt counter incremented (user-facing data integrity check)
        MvcResult statusAfterRetry = mockMvc.perform(get("/api/kyc/status/" + userId)
                .header("Authorization", "Bearer " + userToken)
                .header("X-Tenant-ID", tenantId))
                .andExpect(status().isOk())
                .andReturn();
        int attemptNumber = objectMapper.readTree(statusAfterRetry.getResponse().getContentAsString())
                .get("attemptNumber").asInt();
        assertEquals(2, attemptNumber,
                "Retry should increment attemptNumber to 2");

        // Wait for this attempt to also reach a terminal state, then settle
        waitForTerminal(userId, userToken, tenantId);
        Thread.sleep(2000);

        // ── Rule 6: Daily limit blocks further submissions ────────────────────────
        // Business: maxDailyAttempts=2 means sum(attemptNumber) ≤ 2 per day.
        // After two rounds, the row has attemptNumber=2.
        // The next submit would make sum = 2 (current) + 1 (next increment) = 3 > 2 →
        // blocked.
        mockMvc.perform(multipart("/api/kyc/upload")
                .file(makeJpeg())
                .param("userId", String.valueOf(userId))
                .param("documentType", "PAN")
                .param("documentNumber", docNumber)
                .header("Authorization", "Bearer " + userToken)
                .header("X-Tenant-ID", tenantId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Daily KYC attempt limit reached (2). Please try again tomorrow."));

        // ── Rule 7: Only admins can search all KYC requests ──────────────────────
        // Business: KYC search is a sensitive operation — regular users must not
        // be able to enumerate other users' KYC requests.
        mockMvc.perform(get("/api/kyc/search")
                .header("Authorization", "Bearer " + userToken)
                .header("X-Tenant-ID", tenantId))
                .andExpect(status().isForbidden());

        // ── Rule 8: User can only view their own KYC status ──────────────────────
        // Business: KYC data is personal — a user must not access another user's
        // records.
        // Attempting to access userId=1 (seeded superadmin) must be denied.
        mockMvc.perform(get("/api/kyc/status/1")
                .header("Authorization", "Bearer " + userToken)
                .header("X-Tenant-ID", tenantId))
                .andExpect(status().isForbidden());

        // Sanity: user can still view their own status — it must be terminal (FAILED or
        // VERIFIED)
        MvcResult finalStatus = mockMvc.perform(get("/api/kyc/status/" + userId)
                .header("Authorization", "Bearer " + userToken)
                .header("X-Tenant-ID", tenantId))
                .andExpect(status().isOk())
                .andReturn();
        String terminalStatus = objectMapper.readTree(finalStatus.getResponse().getContentAsString())
                .get("status").asText();
        assertTrue("FAILED".equals(terminalStatus) || "VERIFIED".equals(terminalStatus),
                "Status should be terminal (FAILED or VERIFIED) but was: " + terminalStatus);
    }
}
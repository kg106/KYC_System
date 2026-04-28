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
 * Journey 8 — KYC Resubmission & Retry Journey
 *
 * Business flow:
 * 1. User submits KYC for PAN → SUBMITTED (attempt 1)
 * 2. Processing fails (forced via DB) → FAILED
 * 3. User resubmits PAN → SUBMITTED (attempt 2)
 * createOrReuse() finds FAILED row → UPDATE (no INSERT), attemptNumber++
 * 4. Fails again (forced via DB) → FAILED
 * 5. User resubmits PAN → SUBMITTED (attempt 3)
 * attemptNumber now = 3
 * 6. Fails again (forced via DB) → FAILED
 * 7. User attempts another resubmit → BLOCKED (daily limit)
 *
 * Daily limit logic (createOrReuse):
 * sumAttemptNumber sums the attemptNumber COLUMN, not row count.
 * After attempt 3: SUM = 1 + 2 + 3 = 6 ≥ maxDailyAttempts(5) → blocked.
 *
 * Why the 4th attempt is blocked:
 * The daily limit check runs BEFORE the reuse logic.
 * Sum of attemptNumbers already recorded today = 1+2+3 = 6 ≥ 5 → throw.
 *
 * Error mapping:
 * Daily limit → RuntimeException → GlobalExceptionHandler → 500
 * Duplicate active row → BusinessException → GlobalExceptionHandler → 409
 *
 * All DB state transitions are forced directly via JdbcTemplate
 * because the async worker runs on a daemon thread (real Tesseract OCR
 * will FAIL on the minimal JPEG anyway, but we can't rely on timing).
 */
public class KycResubmissionRetryJourneyTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final byte[] MINIMAL_JPEG = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
            0x00, 0x01, 0x00, 0x00, (byte) 0xFF, (byte) 0xD9
    };

    private MockMultipartFile makeJpeg() {
        return new MockMultipartFile("file", "doc.jpg", "image/jpeg", MINIMAL_JPEG);
    }

    /** Force the user's latest active PAN row to FAILED via direct DB update. */
    private void forceFailPan(Long userId) {
        jdbcTemplate.update(
                "UPDATE kyc_requests SET status = 'FAILED', failure_reason = 'Forced failure for test' " +
                        "WHERE user_id = ? AND document_type = 'PAN' " +
                        "AND status IN ('SUBMITTED', 'PROCESSING', 'PENDING')",
                userId);
    }

    /** Reads the current attemptNumber from the single PAN row for this user. */
    private int getAttemptNumber(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT attempt_number FROM kyc_requests " +
                        "WHERE user_id = ? AND document_type = 'PAN' " +
                        "ORDER BY created_at DESC LIMIT 1",
                Integer.class, userId);
    }

    /** Submits a PAN upload and returns the HTTP status code. */
    private int submitPan(Long userId, String token, String tenantId) throws Exception {
        return mockMvc.perform(multipart("/api/kyc/upload")
                .file(makeJpeg())
                .param("userId", String.valueOf(userId))
                .param("documentType", "PAN")
                .param("documentNumber", "ABCDE1234F")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", tenantId))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    void testKycResubmissionAndRetryJourney() throws Exception {

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String tenantId = "default";

        // ── Setup: Register & login user ──────────────────────────────────────────
        String email = "retry.user." + suffix + "@example.com";
        String password = "Retry@123";
        String mobile = String.format("9%09d", Math.abs(email.hashCode()) % 1_000_000_000L);

        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Retry User","email":"%s","password":"%s",
                         "mobileNumber":"%s","dob":"1992-06-15"}
                        """.formatted(email, password, mobile)))
                .andExpect(status().isCreated())
                .andReturn();
        Long userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asLong();
        String token = loginAndGetToken(email, password);

        // ── Step 1: First submission ──────────────────────────────────────────────
        // Brand new user → no existing row → createOrReuse INSERTs a new row
        // attemptNumber = 1, status = SUBMITTED
        int status1 = submitPan(userId, token, tenantId);
        assertEquals(202, status1,
                "Step 1: First submission must be accepted");

        // Verify DB: one PAN row, attemptNumber = 1
        int totalRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kyc_requests WHERE user_id = ? AND document_type = 'PAN'",
                Integer.class, userId);
        assertEquals(1, totalRows,
                "Step 1: Exactly one PAN row must exist after first submission");
        assertEquals(1, getAttemptNumber(userId),
                "Step 1: attemptNumber must be 1");

        // ── Step 2: Force FAILED & resubmit (attempt 2) ───────────────────────────
        // createOrReuse finds FAILED row → UPDATE same row, attemptNumber = 2
        // Daily sum so far: 1. Limit = 5. 1 < 5 → allowed.
        forceFailPan(userId);

        int status2 = submitPan(userId, token, tenantId);
        assertEquals(202, status2,
                "Step 2: Resubmission after FAILED must be accepted (attempt 2)");

        // Same row reused — still only 1 row total
        int totalRowsAfterResubmit1 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kyc_requests WHERE user_id = ? AND document_type = 'PAN'",
                Integer.class, userId);
        assertEquals(1, totalRowsAfterResubmit1,
                "Step 2: createOrReuse must reuse the existing row — no new INSERT");
        assertEquals(2, getAttemptNumber(userId),
                "Step 2: attemptNumber must be incremented to 2");

        // ── Step 3: Force FAILED again & resubmit (attempt 3) ────────────────────
        // Daily sum so far: 1 + 2 = 3. Limit = 5. 3 < 5 → allowed.
        // After this resubmit: sum will be 1 + 2 + 3 = 6. Limit exceeded for next.
        // ── Step 3: Force FAILED & resubmit (attempt 3) ────────────────────────────
        forceFailPan(userId);
        int status3 = submitPan(userId, token, tenantId);
        assertEquals(202, status3, "Step 3: Second resubmission must be accepted (attempt 3)");
        assertEquals(3, getAttemptNumber(userId), "Step 3: attemptNumber must be 3");

        // ── Step 4: Force FAILED & resubmit (attempt 4) ────────────────────────────
        // Daily sum = current attemptNumber = 3 < 5 → still allowed
        forceFailPan(userId);
        int status4 = submitPan(userId, token, tenantId);
        assertEquals(202, status4, "Step 4: Third resubmission must be accepted (attempt 4)");
        assertEquals(4, getAttemptNumber(userId), "Step 4: attemptNumber must be 4");

        // ── Step 5: Force FAILED & resubmit (attempt 5) ────────────────────────────
        // Daily sum = current attemptNumber = 4 < 5 → still allowed
        forceFailPan(userId);
        int status5 = submitPan(userId, token, tenantId);
        assertEquals(202, status5, "Step 5: Fourth resubmission must be accepted (attempt 5)");
        assertEquals(5, getAttemptNumber(userId), "Step 5: attemptNumber must be 5");

        // ── Step 6: Force FAILED & attempt → BLOCKED ───────────────────────────────
        // Daily sum = current attemptNumber = 5 >= maxDailyAttempts(5) → throws
        // RuntimeException → 500
        forceFailPan(userId);
        int status6 = submitPan(userId, token, tenantId);
        assertEquals(400, status6,
                "Step 6: Must be blocked — daily limit reached (sum 5 >= maxDailyAttempts 5)");

        // attemptNumber must NOT change — limit check fires before reuse logic
        assertEquals(5, getAttemptNumber(userId),
                "Step 6: attemptNumber must remain 5 — row untouched after limit rejection");

        // Total rows still 1 — no new INSERT after daily limit block
        int totalRowsFinal = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kyc_requests WHERE user_id = ? AND document_type = 'PAN'",
                Integer.class, userId);
        assertEquals(1, totalRowsFinal,
                "Step 6: Still exactly one PAN row — no new INSERT after daily limit block");

        // ── Step 7: Verify full history via API ────────────────────────────────────
        MvcResult historyResult = mockMvc.perform(get("/api/kyc/status/all/" + userId)
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", tenantId))
                .andExpect(status().isOk())
                .andReturn();

        String historyBody = historyResult.getResponse().getContentAsString();
        var historyArray = objectMapper.readTree(historyBody);

        assertEquals(1, historyArray.size(),
                "Step 7: History must contain exactly 1 KYC request (the reused row)");
        assertEquals(5, historyArray.get(0).get("attemptNumber").asInt(),
                "Step 7: The single history entry must reflect attemptNumber = 5");
        assertEquals("FAILED", historyArray.get(0).get("status").asText(),
                "Step 7: Final status must be FAILED");
    }
}
package com.example.kyc_system.flow;

import com.example.kyc_system.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Journey 10 — Audit Trail Journey
 *
 * Audit logs are written @Async throughout the system but have never been
 * verified in integration tests. This journey proves that:
 *
 * 1. SUBMIT audit entry is written after KYC upload
 * - action = "SUBMIT"
 * - entityType = "KycRequest"
 * - entityId = the KYC request ID returned by the upload endpoint
 * - performedBy = the user's email (from SecurityContextHolder)
 *
 * 2. VIEW_STATUS audit entry is written after GET /api/kyc/status/{userId}
 * - action = "VIEW_STATUS"
 * - entityType = "KycRequest"
 * - entityId = the KYC request ID
 * - performedBy = the caller's email
 *
 * 3. VIEW_HISTORY audit entry is written after GET /api/kyc/status/all/{userId}
 * - action = "VIEW_HISTORY"
 * - entityType = "User"
 * - entityId = the userId
 * - performedBy = the caller's email
 *
 * Critical implementation detail:
 * logAction() is @Async — it runs on a separate thread pool thread.
 * TenantContext (ThreadLocal) is cleared before the async thread runs.
 * AuditLogServiceImpl has a null-safe fallback: tenant = "system" if null.
 * We must Thread.sleep() after each trigger to let the async write complete
 * before querying the audit_logs table.
 *
 * We assert directly against the audit_logs table via JdbcTemplate
 * because there is no public API endpoint to read audit logs.
 */
public class AuditTrailJourneyTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final byte[] MINIMAL_JPEG = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
            0x00, 0x01, 0x00, 0x00, (byte) 0xFF, (byte) 0xD9
    };

    /**
     * Polls the audit_logs table until the expected action appears or timeout.
     * Needed because logAction() is @Async — DB write happens on a separate thread.
     *
     * @param action      e.g. "SUBMIT", "VIEW_STATUS", "VIEW_HISTORY"
     * @param entityType  e.g. "KycRequest", "User"
     * @param entityId    the ID to match
     * @param performedBy the actor email to match
     * @param timeoutMs   max wait in milliseconds
     */
    private void waitForAuditLog(String action, String entityType, Long entityId,
            String performedBy, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs " +
                            "WHERE action = ? AND entity_type = ? AND entity_id = ? AND performed_by = ?",
                    Integer.class,
                    action, entityType, entityId, performedBy);
            if (count != null && count > 0)
                return;
            Thread.sleep(200);
        }
        fail("Timed out waiting for audit log: action=" + action +
                ", entityType=" + entityType + ", entityId=" + entityId +
                ", performedBy=" + performedBy);
    }

    @Test
    void testAuditTrailJourney() throws Exception {

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String tenantId = "default";

        // ── Setup: Register user ──────────────────────────────────────────────────
        String userEmail = "audit.user." + suffix + "@example.com";
        String password = "Audit@123";
        String mobile = String.format("7%09d", Math.abs(userEmail.hashCode()) % 1_000_000_000L);

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Audit User","email":"%s","password":"%s",
                         "mobileNumber":"%s","dob":"1993-04-12"}
                        """.formatted(userEmail, password, mobile)))
                .andExpect(status().isCreated())
                .andReturn();

        Long userId = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .get("id").asLong();
        String userToken = loginAndGetToken(userEmail, password);

        // ── Step 1: Upload KYC → triggers SUBMIT audit log ───────────────────────
        MockMultipartFile doc = new MockMultipartFile(
                "file", "pan.jpg", "image/jpeg", MINIMAL_JPEG);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/kyc/upload")
                .file(doc)
                .param("userId", String.valueOf(userId))
                .param("documentType", "PAN")
                .param("documentNumber", "ABCDE1234F")
                .header("Authorization", "Bearer " + userToken)
                .header("X-Tenant-ID", tenantId))
                .andExpect(status().isAccepted())
                .andReturn();

        Long kycRequestId = objectMapper.readTree(uploadResult.getResponse().getContentAsString())
                .get("requestId").asLong();

        // Wait for async SUBMIT log to be written
        // logAction("SUBMIT", "KycRequest", savedRequest.getId(), ..., userEmail)
        waitForAuditLog("SUBMIT", "KycRequest", kycRequestId, userEmail, 5000);

        // Assert SUBMIT log details from DB
        List<Map<String, Object>> submitLogs = jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE action = ? AND entity_type = ? AND entity_id = ?",
                "SUBMIT", "KycRequest", kycRequestId);

        assertEquals(1, submitLogs.size(),
                "Step 1: Exactly one SUBMIT audit entry must exist for this KYC request");

        Map<String, Object> submitLog = submitLogs.get(0);
        assertEquals("SUBMIT", submitLog.get("action"),
                "Step 1: action must be SUBMIT");
        assertEquals("KycRequest", submitLog.get("entity_type"),
                "Step 1: entityType must be KycRequest");
        assertEquals(kycRequestId, ((Number) submitLog.get("entity_id")).longValue(),
                "Step 1: entityId must match the KYC request ID");
        assertEquals(userEmail, submitLog.get("performed_by"),
                "Step 1: performedBy must be the uploading user's email");

        // ── Step 2: View KYC status → triggers VIEW_STATUS audit log ─────────────
        // GET /api/kyc/status/{userId} → getLatestByUser() → logAction("VIEW_STATUS",
        // ...)
        mockMvc.perform(get("/api/kyc/status/" + userId)
                .header("Authorization", "Bearer " + userToken)
                .header("X-Tenant-ID", tenantId))
                .andExpect(status().isOk());

        // Wait for async VIEW_STATUS log
        // logAction("VIEW_STATUS", "KycRequest", request.getId(), ..., userEmail)
        waitForAuditLog("VIEW_STATUS", "KycRequest", kycRequestId, userEmail, 5000);

        List<Map<String, Object>> viewStatusLogs = jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE action = ? AND entity_type = ? AND entity_id = ? AND performed_by = ?",
                "VIEW_STATUS", "KycRequest", kycRequestId, userEmail);

        assertFalse(viewStatusLogs.isEmpty(),
                "Step 2: At least one VIEW_STATUS audit entry must exist");

        Map<String, Object> viewStatusLog = viewStatusLogs.get(0);
        assertEquals("VIEW_STATUS", viewStatusLog.get("action"),
                "Step 2: action must be VIEW_STATUS");
        assertEquals("KycRequest", viewStatusLog.get("entity_type"),
                "Step 2: entityType must be KycRequest");
        assertEquals(kycRequestId, ((Number) viewStatusLog.get("entity_id")).longValue(),
                "Step 2: entityId must match the KYC request ID");
        assertEquals(userEmail, viewStatusLog.get("performed_by"),
                "Step 2: performedBy must be the user who called the status endpoint");

        // ── Step 3: View KYC history → triggers VIEW_HISTORY audit log ───────────
        // GET /api/kyc/status/all/{userId} → getAllByUser() →
        // logAction("VIEW_HISTORY", "User", userId, ..., userEmail)
        mockMvc.perform(get("/api/kyc/status/all/" + userId)
                .header("Authorization", "Bearer " + userToken)
                .header("X-Tenant-ID", tenantId))
                .andExpect(status().isOk());

        // Wait for async VIEW_HISTORY log
        // Note: entityType = "User", entityId = userId (not kycRequestId)
        waitForAuditLog("VIEW_HISTORY", "User", userId, userEmail, 5000);

        List<Map<String, Object>> viewHistoryLogs = jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE action = ? AND entity_type = ? AND entity_id = ? AND performed_by = ?",
                "VIEW_HISTORY", "User", userId, userEmail);

        assertFalse(viewHistoryLogs.isEmpty(),
                "Step 3: At least one VIEW_HISTORY audit entry must exist");

        Map<String, Object> viewHistoryLog = viewHistoryLogs.get(0);
        assertEquals("VIEW_HISTORY", viewHistoryLog.get("action"),
                "Step 3: action must be VIEW_HISTORY");
        assertEquals("User", viewHistoryLog.get("entity_type"),
                "Step 3: entityType must be User (not KycRequest)");
        assertEquals(userId, ((Number) viewHistoryLog.get("entity_id")).longValue(),
                "Step 3: entityId must be the userId (not the KYC request ID)");
        assertEquals(userEmail, viewHistoryLog.get("performed_by"),
                "Step 3: performedBy must be the user who called the history endpoint");

        // ── Step 4: Admin views same user's status → separate audit entry ─────────
        // Proves that the actor (performedBy) is correctly captured per-caller.
        // Admin calls VIEW_STATUS → audit log must record ADMIN's email, not user's.
        String adminToken = loginAndGetToken("admin@kyc.com", "Password@123");

        mockMvc.perform(get("/api/kyc/status/" + userId)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", tenantId))
                .andExpect(status().isOk());

        waitForAuditLog("VIEW_STATUS", "KycRequest", kycRequestId, "admin@kyc.com", 5000);

        List<Map<String, Object>> adminViewLogs = jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE action = ? AND entity_type = ? AND entity_id = ? AND performed_by = ?",
                "VIEW_STATUS", "KycRequest", kycRequestId, "admin@kyc.com");

        assertFalse(adminViewLogs.isEmpty(),
                "Step 4: VIEW_STATUS audit entry must exist for admin actor");
        assertEquals("admin@kyc.com", adminViewLogs.get(0).get("performed_by"),
                "Step 4: performedBy must be admin's email — not the data owner's email");

        // ── Step 5: Cross-check total audit entries for this KYC request ──────────
        // SUBMIT(1) + VIEW_STATUS by user(1) + VIEW_STATUS by admin(1) = 3 entries
        // for entityType=KycRequest, entityId=kycRequestId
        int totalKycRequestLogs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = ? AND entity_id = ?",
                Integer.class, "KycRequest", kycRequestId);

        assertTrue(totalKycRequestLogs >= 3,
                "Step 5: At least 3 audit entries must exist for this KYC request " +
                        "(SUBMIT + VIEW_STATUS by user + VIEW_STATUS by admin). Found: " + totalKycRequestLogs);
    }
}
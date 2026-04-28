package com.example.kyc_system.flow;

import com.example.kyc_system.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ============================================================
 * KYC FULL HISTORY — GET /api/kyc/status/all/{userId}
 * ============================================================
 *
 * WHAT WE'RE TESTING:
 * GET /api/kyc/status/all/{userId} → KycController.getAllKycStatus()
 * → KycRequestService.getAllByUser()
 * → Returns a LIST of all KYC requests for a user (not just the latest)
 *
 * WHY IS THIS DIFFERENT FROM GET /api/kyc/status/{userId}?
 * /status/{userId} → returns only the LATEST KYC request (findTop1)
 * /status/all/{userId} → returns ALL KYC requests ever submitted (full list)
 *
 * KEY SCENARIOS:
 * 1. User with no KYC history → 404 "No KYC history found"
 * 2. User views own single submission → 200, list of size 1
 * 3. User views own multiple docs → 200, list of size N (PAN + AADHAAR)
 * 4. Cross-user access → 403 (canAccessUser block)
 * 5. Admin views another user's history→ 200 (admin bypasses canAccessUser)
 * 6. Unauthenticated request → 401/403
 *
 * NOTE ON HISTORY SIZE:
 * createOrReuse() REUSES the same DB row on re-submission for the same
 * document type. So submitting PAN twice = 1 row, not 2.
 * But submitting PAN + AADHAAR = 2 rows.
 * This test relies on different documentTypes to get a list of size > 1.
 * ============================================================
 */
public class KycHistoryIntegrationTest extends BaseIntegrationTest {

    private static final String TENANT = "default";

    // ── Scenario 1: User with NO KYC history → 404 ───────────────────────────

    @Test
    void shouldReturn404WhenUserHasNoKycHistory() throws Exception {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String email = "nokyc.history." + uniqueId + "@example.com";
        String mobile = String.format("7%09d", Math.abs(email.hashCode()) % 1_000_000_000L);

        // Register user but never submit any KYC
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"No History User","email":"%s","password":"NoHistory@123",
                         "mobileNumber":"%s","dob":"1995-03-10"}
                        """.formatted(email, mobile)))
                .andExpect(status().isCreated())
                .andReturn();

        Long userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asLong();
        String token = loginAndGetToken(email, "NoHistory@123");

        // ── ACT & ASSERT ─────────────────────────────────────────────────────
        mockMvc.perform(get("/api/kyc/status/all/" + userId)
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No KYC history found for this user"));
    }

    // ── Scenario 2: User with ONE submission sees a list of size 1 ───────────

    @Test
    void shouldReturnListOfOneAfterSingleSubmission() throws Exception {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String email = "one.submit." + uniqueId + "@example.com";
        String mobile = String.format("7%09d", Math.abs(email.hashCode()) % 1_000_000_000L);

        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"One Submit User","email":"%s","password":"OneSubmit@123",
                         "mobileNumber":"%s","dob":"1993-07-21"}
                        """.formatted(email, mobile)))
                .andExpect(status().isCreated())
                .andReturn();

        Long userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asLong();
        String token = loginAndGetToken(email, "OneSubmit@123");

        // Submit one PAN document
        mockMvc.perform(multipart("/api/kyc/upload")
                .file(new MockMultipartFile("file", "pan.jpg", "image/jpeg", "bytes".getBytes()))
                .param("userId", userId.toString())
                .param("documentType", "PAN")
                .param("documentNumber", "ABCDE1234F")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", TENANT))
                .andExpect(status().isAccepted());

        // ── ACT & ASSERT ─────────────────────────────────────────────────────
        MvcResult result = mockMvc.perform(get("/api/kyc/status/all/" + userId)
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(body);

        assertTrue(arr.isArray(), "Response must be a JSON array");
        assertEquals(1, arr.size(), "Exactly 1 KYC row expected (one submission)");

        // Verify the returned record has the expected fields
        com.fasterxml.jackson.databind.JsonNode first = arr.get(0);
        assertNotNull(first.get("requestId"), "requestId must be present");
        assertNotNull(first.get("status"), "status must be present");
        assertNotNull(first.get("attemptNumber"), "attemptNumber must be present");
    }

    // ── Scenario 3: User submits PAN + AADHAAR → history shows both rows ─────
    //
    // createOrReuse() uses a partial unique index on (user_id, document_type)
    // WHERE status IN (PENDING, SUBMITTED, PROCESSING).
    // Different document types = different rows → list size = 2.

    @Test
    void shouldReturnMultipleRowsForDifferentDocumentTypes() throws Exception {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String email = "multi.doc." + uniqueId + "@example.com";
        String mobile = String.format("7%09d", Math.abs(email.hashCode()) % 1_000_000_000L);

        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Multi Doc User","email":"%s","password":"MultiDoc@123",
                         "mobileNumber":"%s","dob":"1990-01-15"}
                        """.formatted(email, mobile)))
                .andExpect(status().isCreated())
                .andReturn();

        Long userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asLong();
        String token = loginAndGetToken(email, "MultiDoc@123");

        // Submit PAN
        mockMvc.perform(multipart("/api/kyc/upload")
                .file(new MockMultipartFile("file", "pan.jpg", "image/jpeg", "bytes".getBytes()))
                .param("userId", userId.toString())
                .param("documentType", "PAN")
                .param("documentNumber", "PANNO1234A")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", TENANT))
                .andExpect(status().isAccepted());

        // Submit AADHAAR (different document type → separate row)
        mockMvc.perform(multipart("/api/kyc/upload")
                .file(new MockMultipartFile("file", "aadhaar.jpg", "image/jpeg", "bytes".getBytes()))
                .param("userId", userId.toString())
                .param("documentType", "AADHAAR")
                .param("documentNumber", "1234-5678-9012")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", TENANT))
                .andExpect(status().isAccepted());

        // ── ACT & ASSERT ─────────────────────────────────────────────────────
        MvcResult result = mockMvc.perform(get("/api/kyc/status/all/" + userId)
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(body);

        assertTrue(arr.isArray(), "Response must be a JSON array");
        assertEquals(2, arr.size(), "Expected 2 rows — one for PAN, one for AADHAAR");

        // Verify both document types appear in the response
        boolean hasPan = false;
        boolean hasAadhaar = false;
        for (com.fasterxml.jackson.databind.JsonNode node : arr) {
            String docType = node.path("documentType").asText("");
            if ("PAN".equals(docType))
                hasPan = true;
            if ("AADHAAR".equals(docType))
                hasAadhaar = true;
        }
        assertTrue(hasPan, "PAN submission must appear in history");
        assertTrue(hasAadhaar, "AADHAAR submission must appear in history");
    }

    // ── Scenario 4: Cross-user access → 403 ──────────────────────────────────

    @Test
    void shouldBlockUserFromViewingAnotherUsersHistory() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        // Register User A
        String emailA = "hist.userA." + uid + "@example.com";
        String mobileA = String.format("8%09d", Math.abs(emailA.hashCode()) % 1_000_000_000L);
        MvcResult regA = mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"History UserA","email":"%s","password":"HistA@1234",
                         "mobileNumber":"%s","dob":"1992-04-22"}
                        """.formatted(emailA, mobileA)))
                .andExpect(status().isCreated())
                .andReturn();
        Long userAId = objectMapper.readTree(regA.getResponse().getContentAsString()).get("id").asLong();

        // Register User B
        String emailB = "hist.userB." + uid + "@example.com";
        String mobileB = String.format("8%09d", Math.abs(emailB.hashCode()) % 1_000_000_000L);
        MvcResult regB = mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"History UserB","email":"%s","password":"HistB@1234",
                         "mobileNumber":"%s","dob":"1994-08-11"}
                        """.formatted(emailB, mobileB)))
                .andExpect(status().isCreated())
                .andReturn();
        Long userBId = objectMapper.readTree(regB.getResponse().getContentAsString()).get("id").asLong();

        // Login as User A
        String tokenA = loginAndGetToken(emailA, "HistA@1234");

        // ── ACT: User A tries to access User B's history → 403 ───────────────
        // @PreAuthorize("@securityService.canAccessUser(#userId)")
        // User A's JWT userId ≠ userBId → false → 403
        mockMvc.perform(get("/api/kyc/status/all/" + userBId)
                .header("Authorization", "Bearer " + tokenA)
                .header("X-Tenant-ID", TENANT))
                .andExpect(status().isForbidden());
    }

    // ── Scenario 5: Admin views another user's full history → 200 ────────────
    //
    // @PreAuthorize("@securityService.canAccessUser(#userId)")
    // canAccessUser() returns true for ROLE_ADMIN regardless of userId

    @Test
    void shouldAllowAdminToViewAnyUsersHistory() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "hist.admin.target." + uid + "@example.com";
        String mobile = String.format("8%09d", Math.abs(email.hashCode()) % 1_000_000_000L);

        // Register a regular user and submit KYC
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Admin Target User","email":"%s","password":"Target@1234",
                         "mobileNumber":"%s","dob":"1991-11-30"}
                        """.formatted(email, mobile)))
                .andExpect(status().isCreated())
                .andReturn();
        Long targetUserId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asLong();
        String userToken = loginAndGetToken(email, "Target@1234");

        // Submit one KYC document as that user
        mockMvc.perform(multipart("/api/kyc/upload")
                .file(new MockMultipartFile("file", "doc.jpg", "image/jpeg", "bytes".getBytes()))
                .param("userId", targetUserId.toString())
                .param("documentType", "PAN")
                .param("documentNumber", "ADMINTEST1F")
                .header("Authorization", "Bearer " + userToken)
                .header("X-Tenant-ID", TENANT))
                .andExpect(status().isAccepted());

        // Login as admin (seeded: admin@kyc.com / Password@123)
        String adminToken = loginAndGetToken("admin@kyc.com", "Password@123");

        // ── ACT: Admin views the user's full history → 200 ───────────────────
        MvcResult result = mockMvc.perform(get("/api/kyc/status/all/" + targetUserId)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(body);

        assertTrue(arr.isArray(), "Admin response must be a JSON array");
        assertEquals(1, arr.size(), "Must have exactly 1 KYC row");

        // Admin should see masked document number
        // formatKycResponse() masks if caller has ROLE_ADMIN
        // (only visible after OCR completes — doc number lives on KycExtractedData)
        // We assert the structure is correct; masking is covered in the masking test.
        assertNotNull(arr.get(0).get("requestId"), "requestId must be present");
        assertNotNull(arr.get(0).get("status"), "status must be present");
    }

    // ── Scenario 6: Unauthenticated request → 401/403 ────────────────────────

    @Test
    void shouldRejectUnauthenticatedRequestToHistory() throws Exception {
        // No Authorization header at all
        mockMvc.perform(get("/api/kyc/status/all/1")
                .header("X-Tenant-ID", TENANT))
                // JwtAuthenticationFilter finds no token → Spring Security rejects
                .andExpect(status().is4xxClientError());
    }
}
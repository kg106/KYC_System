package com.example.kyc_system.kyc;

import com.example.kyc_system.base.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================
 * KYC UPLOAD & STATUS — INTEGRATION TESTS
 * ============================================================
 *
 * WHAT WE'RE TESTING:
 * The full KYC submission flow:
 * POST /api/kyc/upload → KycController
 * → KycOrchestrationService
 * → KycRequestService (save to DB)
 * → KycDocumentService (save file)
 * → KycQueueService (push to Redis queue)
 *
 * And status check:
 * GET /api/kyc/status/{userId} → KycRequestService
 * → KycRequestRepository (query DB)
 *
 * HOW IS THIS DIFFERENT FROM A UNIT TEST?
 * Unit test for KycOrchestrationService would mock ALL dependencies.
 * This test uses the REAL services, REAL database, and REAL Redis.
 *
 * WHY NOT TEST OCR HERE?
 * Tesseract OCR requires the binary installed on the machine.
 * In tests, we use a small dummy image. The OCR will likely fail or
 * return garbage — that's fine, we're testing the HTTP flow, not OCR accuracy.
 * For OCR, you'd mock OcrService in a separate unit test.
 */
@DisplayName("KYC API — Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KycIntegrationTest extends BaseIntegrationTest {

    private static final String TENANT_ID = "default";
    private static final String USER_EMAIL = "kycuser@example.com";
    private static final String USER_PASS = "KycPass@123";

    // These are shared across tests in this class
    private Long userId;
    private String accessToken;

    /**
     * @BeforeEach runs before EVERY test method in this class.
     *             We register a fresh user and log in to get a JWT token.
     *
     *             WHY FRESH USER EACH TIME?
     *             To avoid test interference — each test starts with a clean slate.
     *             This is the "test isolation" principle.
     *
     *             TRADE-OFF: Slightly slower. For speed, use @BeforeAll (once per
     *             class).
     *             For correctness, use @BeforeEach (once per test). We choose
     *             correctness.
     */
    @BeforeEach
    void setUpUser() throws Exception {
        // Use a unique email per test run using a timestamp
        // This prevents "email already exists" errors when tests run in sequence
        String uniqueEmail = "kycuser_" + System.currentTimeMillis() + "@example.com";
        userId = registerUser("KYC User", uniqueEmail, USER_PASS, TENANT_ID);
        accessToken = loginAndGetToken(uniqueEmail, USER_PASS);
    }

    // ============================================================
    // TEST 1: Happy Path — Upload a KYC document
    // ============================================================

    @Test
    @Order(1)
    @DisplayName("✅ Should accept KYC document upload and return requestId")
    void shouldAcceptKycDocumentUpload() throws Exception {
        // ── ARRANGE ─────────────────────────────────────────────

        /**
         * MockMultipartFile — Simulates a real file upload without needing a real file.
         *
         * Parameters:
         * "file" → The form field name (must match @RequestParam("file") in controller)
         * "dummy-pan.jpg" → The filename
         * "image/jpeg" → The content type (MIME type)
         * bytes → The actual file content (we use tiny fake bytes)
         *
         * In real testing, you'd load an actual PAN card image.
         * For this test, we just want to confirm the endpoint accepts the request.
         */
        MockMultipartFile fakeDocument = new MockMultipartFile(
                "file",
                "pan-card.jpg",
                "image/jpeg",
                "fake-image-content".getBytes() // Tiny fake image bytes
        );

        // ── ACT & ASSERT ─────────────────────────────────────────

        /**
         * multipart() is used for file upload requests (multipart/form-data).
         * This is different from post() which is used for JSON bodies.
         *
         * .file(fakeDocument) adds the file to the multipart request.
         * .param(...) adds regular form fields.
         */
        mockMvc.perform(
                multipart("/api/kyc/upload")
                        .file(fakeDocument)
                        .param("userId", userId.toString())
                        .param("documentType", "PAN")
                        .param("documentNumber", "ABCDE1234F")
                        // Required security headers
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Tenant-ID", TENANT_ID)
                        .contentType(MediaType.MULTIPART_FORM_DATA))

                // 202 Accepted = "I got your request, processing started"
                // (Not 200 OK because processing is async via Redis queue)
                .andExpect(status().isAccepted())

                // Response: {"message": "KYC request submitted...", "requestId": 1}
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.message").value("KYC request submitted successfully"));
    }

    // ============================================================
    // TEST 2: Status Check — After submitting, status should be SUBMITTED
    // ============================================================

    @Test
    @Order(2)
    @DisplayName("✅ Should return KYC status SUBMITTED after upload")
    void shouldReturnSubmittedStatusAfterUpload() throws Exception {
        // ── ARRANGE: Submit a KYC document ───────────────────────
        MockMultipartFile fakeDoc = new MockMultipartFile(
                "file", "aadhaar.jpg", "image/jpeg", "fake-bytes".getBytes());

        mockMvc.perform(
                multipart("/api/kyc/upload")
                        .file(fakeDoc)
                        .param("userId", userId.toString())
                        .param("documentType", "AADHAAR")
                        .param("documentNumber", "1234-5678-9012")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isAccepted());

        // ── ACT: Check the status ─────────────────────────────────
        MvcResult statusResult = mockMvc.perform(
                get("/api/kyc/status/" + userId)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isOk())
                .andReturn();

        // ── ASSERT ───────────────────────────────────────────────
        String body = statusResult.getResponse().getContentAsString();
        String status = objectMapper.readTree(body).get("status").asText();

        // Status should be SUBMITTED (async processing hasn't run yet)
        // In a real test, you'd wait or mock the async worker.
        assertTrue(
                status.equals("SUBMITTED") || status.equals("PROCESSING"),
                "Status should be SUBMITTED or PROCESSING but was: " + status);
    }

    // ============================================================
    // TEST 3: Sad Path — Unauthorized upload (no JWT)
    // ============================================================

    @Test
    @Order(3)
    @DisplayName("❌ Should reject upload without Authorization header")
    void shouldRejectUploadWithoutJwt() throws Exception {
        MockMultipartFile fakeDoc = new MockMultipartFile(
                "file", "doc.jpg", "image/jpeg", "bytes".getBytes());

        mockMvc.perform(
                multipart("/api/kyc/upload")
                        .file(fakeDoc)
                        .param("userId", userId.toString())
                        .param("documentType", "PAN")
                        .param("documentNumber", "ABCDE1234F")
                        .header("X-Tenant-ID", TENANT_ID)
                        // NOTE: No Authorization header!
                        .contentType(MediaType.MULTIPART_FORM_DATA))

                // JwtAuthenticationFilter won't find a token → 401 Unauthorized
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // TEST 4: Sad Path — Can't upload for another user (RBAC)
    // ============================================================

    @Test
    @Order(4)
    @DisplayName("❌ Should block a user from uploading KYC for another user's ID")
    void shouldBlockUploadForOtherUserId() throws Exception {
        // ── ARRANGE: Create a second user ─────────────────────────
        Long otherUserId = registerUser(
                "Other User", "other_" + System.currentTimeMillis() + "@test.com",
                "OtherPass@123", TENANT_ID);

        MockMultipartFile fakeDoc = new MockMultipartFile(
                "file", "doc.jpg", "image/jpeg", "bytes".getBytes());

        // ── ACT: User 1 tries to upload KYC for User 2's account ──
        mockMvc.perform(
                multipart("/api/kyc/upload")
                        .file(fakeDoc)
                        // ← Trying to upload for otherUserId using OUR token
                        .param("userId", otherUserId.toString())
                        .param("documentType", "PAN")
                        .param("documentNumber", "ABCDE1234F")
                        .header("Authorization", "Bearer " + accessToken) // ← Our JWT
                        .header("X-Tenant-ID", TENANT_ID))

                // @PreAuthorize("@securityService.canAccessUser(#userId)")
                // This evaluates to false because our JWT userId ≠ otherUserId
                // → Spring Security throws AccessDeniedException → 403 Forbidden
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // TEST 5: Sad Path — Status check for non-existent user
    // ============================================================

    @Test
    @Order(5)
    @DisplayName("✅ Should return 404 when checking status for user with no KYC")
    void shouldReturn404WhenNoKycRequestExists() throws Exception {
        // This user exists but has no KYC submissions
        mockMvc.perform(
                get("/api/kyc/status/" + userId)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Tenant-ID", TENANT_ID))

                // KycController returns 404 when no KYC request found for user
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No KYC request found for this user"));
    }

    // ============================================================
    // TEST 6: Sad Path — Duplicate submission (same doc type, already in progress)
    // ============================================================

    @Test
    @Order(6)
    @DisplayName("❌ Should reject a second KYC submission while one is already in progress")
    void shouldRejectDuplicateKycSubmission() throws Exception {
        MockMultipartFile doc1 = new MockMultipartFile(
                "file", "pan1.jpg", "image/jpeg", "bytes".getBytes());
        MockMultipartFile doc2 = new MockMultipartFile(
                "file", "pan2.jpg", "image/jpeg", "bytes".getBytes());

        // ── First submission — should succeed ─────────────────────
        mockMvc.perform(
                multipart("/api/kyc/upload")
                        .file(doc1)
                        .param("userId", userId.toString())
                        .param("documentType", "PAN")
                        .param("documentNumber", "ABCDE1234F")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isAccepted());

        // ── Second submission with same doc type — should be rejected ──
        // KycRequestServiceImpl checks: if status is PENDING/SUBMITTED/PROCESSING
        // → throws RuntimeException("Only one KYC request...can be processed at a
        // time")
        mockMvc.perform(
                multipart("/api/kyc/upload")
                        .file(doc2)
                        .param("userId", userId.toString())
                        .param("documentType", "PAN") // ← Same document type!
                        .param("documentNumber", "ABCDE1234F")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isBadRequest()); // Or 4xx depending on your exception handler
    }
}
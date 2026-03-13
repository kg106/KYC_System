package com.example.kyc_system.flow;

import com.example.kyc_system.BaseIntegrationTest;
import com.example.kyc_system.entity.*;
import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ============================================================
 * DOCUMENT NUMBER MASKING — GAP #6
 * ============================================================
 *
 * WHAT IS BEING TESTED:
 * formatKycResponse() in KycController contains this logic:
 *
 * String docNumber = data.getExtractedDocumentNumber();
 * if (SecurityContextHolder...hasRole("ROLE_ADMIN")) {
 * docNumber = MaskingUtil.maskDocumentNumber(docNumber);
 * }
 * response.put("extractedDocumentNumber", docNumber);
 *
 * MaskingUtil.maskDocumentNumber("ABCDE1234F"):
 * length=10, lastFour="234F", maskedPart="******"
 * → "******234F"
 *
 * So:
 * - ROLE_USER calling GET /api/kyc/status/{userId} → sees "ABCDE1234F" (plain)
 * - ROLE_ADMIN calling GET /api/kyc/status/{userId} → sees "******234F"
 * (masked)
 *
 * WHY THIS MATTERS:
 * This logic was NEVER covered by any existing test. If someone accidentally
 * inverts the condition (masks for user, not admin) or removes the masking
 * entirely, no test would catch it. This gap is a data-privacy regression risk.
 *
 * SEEDING STRATEGY:
 * The masking field (extractedDocumentNumber) only appears in the response
 * when KycExtractedData exists on the document. Since the async OCR pipeline
 * never completes in tests, we seed the full chain directly:
 * KycRequest → KycDocument → KycExtractedData
 * Then call GET /api/kyc/status/{userId} as user (canAccessUser) and as admin.
 *
 * SCENARIOS COVERED:
 * 1. USER calls status → extractedDocumentNumber is plain (unmasked)
 * 2. ADMIN calls status → extractedDocumentNumber is masked (last 4 visible)
 * 3. Masking leaves exactly last 4 chars visible, rest replaced with '*'
 * 4. Short doc number (≤4 chars) → not masked even for admin (MaskingUtil
 * passthrough)
 * ============================================================
 */
public class DocumentMaskingIntegrationTest extends BaseIntegrationTest {

    private static final String TENANT = "default";
    private static final String DOC_NUMBER = "ABCDE1234F"; // 10-char PAN number
    private static final String LAST_FOUR = "234F";
    private static final String SHORT_DOC = "AB12"; // ≤4 chars → no masking

    @Autowired
    private KycRequestRepository kycRequestRepository;
    @Autowired
    private KycDocumentRepository kycDocumentRepository;
    @Autowired
    private KycExtractedDataRepository kycExtractedDataRepository;
    @Autowired
    private UserRepository userRepository;

    /**
     * Seeds the full KycRequest → KycDocument → KycExtractedData chain
     * for a given user. Returns the userId.
     */
    private Long seedKycWithExtractedData(String docNumber) throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "mask." + uid + "@example.com";
        String mobile = String.format("%010d",
                (Math.abs(uid.hashCode()) % 9_000_000_000L) + 1_000_000_000L);

        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Mask User","email":"%s","password":"Masking@123",
                         "mobileNumber":"%s","dob":"1990-03-15"}
                        """.formatted(email, mobile)))
                .andExpect(status().isCreated())
                .andReturn();

        Long userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asLong();

        com.example.kyc_system.entity.User user = userRepository.findById(userId)
                .orElseThrow(() -> new AssertionError("User not seeded: " + userId));

        // 1. KycRequest (VERIFIED status so formatKycResponse includes document data)
        KycRequest request = kycRequestRepository.save(KycRequest.builder()
                .user(user)
                .documentType(DocumentType.PAN.name())
                .status(KycStatus.VERIFIED.name())
                .tenantId(TENANT)
                .attemptNumber(1)
                .submittedAt(LocalDateTime.now().minusHours(2))
                .completedAt(LocalDateTime.now().minusMinutes(30))
                .build());

        // 2. KycDocument
        KycDocument document = kycDocumentRepository.save(KycDocument.builder()
                .kycRequest(request)
                .tenantId(TENANT)
                .documentType(DocumentType.PAN.name())
                .documentNumber(docNumber)
                .documentPath("uploads/pan.jpg")
                .documentHash("abc123def456abc123def456abc123def456abc123def456abc123def456abc1")
                .mimeType("image/jpeg")
                .fileSize(102400L)
                .encrypted(true)
                .uploadedAt(LocalDateTime.now().minusHours(2))
                .build());

        // 3. KycExtractedData — this is what formatKycResponse reads for masking
        kycExtractedDataRepository.save(KycExtractedData.builder()
                .kycDocument(document)
                .extractedName("Mask User")
                .extractedDob(LocalDate.of(1990, 3, 15))
                .extractedDocumentNumber(docNumber)
                .rawOcrResponse(Map.of("text", "mocked ocr output"))
                .build());

        return userId;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 1: USER sees unmasked document number
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void user_shouldSeeUnmaskedDocumentNumber() throws Exception {
        Long userId = seedKycWithExtractedData(DOC_NUMBER);

        // Get a fresh token for this user
        // We need the email — re-derive it from the user entity
        com.example.kyc_system.entity.User user = userRepository.findById(userId)
                .orElseThrow();
        String userToken = loginAndGetToken(user.getEmail(), "Masking@123");

        MvcResult result = mockMvc.perform(get("/api/kyc/status/" + userId)
                .header("Authorization", "Bearer " + userToken)
                .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        var node = objectMapper.readTree(body);

        // extractedDocumentNumber must exist and be the PLAIN value
        assertNotNull(node.get("extractedDocumentNumber"),
                "Response must include extractedDocumentNumber");

        String returned = node.get("extractedDocumentNumber").asText();

        assertEquals(DOC_NUMBER, returned,
                "USER must see the PLAIN document number '" + DOC_NUMBER +
                        "', but got: '" + returned + "'");

        // Confirm it does NOT contain masking asterisks
        assertFalse(returned.contains("*"),
                "USER must NOT see masked value. Got: " + returned);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 2: ADMIN sees masked document number
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void admin_shouldSeeMaskedDocumentNumber() throws Exception {
        Long userId = seedKycWithExtractedData(DOC_NUMBER);

        String adminToken = loginAndGetToken("admin@kyc.com", "Password@123");

        MvcResult result = mockMvc.perform(get("/api/kyc/status/" + userId)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        var node = objectMapper.readTree(body);

        assertNotNull(node.get("extractedDocumentNumber"),
                "Response must include extractedDocumentNumber");

        String returned = node.get("extractedDocumentNumber").asText();

        // Must NOT be the plain value
        assertNotEquals(DOC_NUMBER, returned,
                "ADMIN must NOT see the plain document number. Got: " + returned);

        // Must contain asterisks
        assertTrue(returned.contains("*"),
                "ADMIN must see masked value with '*'. Got: " + returned);

        // Last 4 chars must match
        assertTrue(returned.endsWith(LAST_FOUR),
                "Masked value must end with last 4 chars '" + LAST_FOUR +
                        "'. Got: " + returned);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 3: Exact masking format — length-4 chars are '*', last 4 are plain
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void admin_maskedValueHasCorrectFormat() throws Exception {
        Long userId = seedKycWithExtractedData(DOC_NUMBER);

        String adminToken = loginAndGetToken("admin@kyc.com", "Password@123");

        MvcResult result = mockMvc.perform(get("/api/kyc/status/" + userId)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andReturn();

        String returned = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("extractedDocumentNumber").asText();

        // "ABCDE1234F" → length 10 → 6 stars + "234F"
        int expectedLength = DOC_NUMBER.length();
        int expectedStarCount = DOC_NUMBER.length() - 4;
        String expectedMasked = "*".repeat(expectedStarCount) + LAST_FOUR;

        assertEquals(expectedLength, returned.length(),
                "Masked value length must equal original length (" + expectedLength +
                        "). Got: " + returned);
        assertEquals(expectedMasked, returned,
                "Masked value must be '" + expectedMasked + "'. Got: " + returned);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCENARIO 4: Short doc number (≤4 chars) → no masking even for admin
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void admin_shortDocNumber_shouldNotBeMasked() throws Exception {
        // MaskingUtil: if length <= 4 → return as-is (no masking)
        Long userId = seedKycWithExtractedData(SHORT_DOC);

        String adminToken = loginAndGetToken("admin@kyc.com", "Password@123");

        MvcResult result = mockMvc.perform(get("/api/kyc/status/" + userId)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andReturn();

        String returned = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("extractedDocumentNumber").asText();

        // MaskingUtil passthrough: length ≤ 4 → unchanged
        assertEquals(SHORT_DOC, returned,
                "Short doc number (≤4 chars) must not be masked even for ADMIN. Got: " + returned);
    }
}
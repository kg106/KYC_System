package com.example.kyc_system.flow;

import com.example.kyc_system.BaseIntegrationTest;
import com.example.kyc_system.entity.*;
import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * File 3 — User Query + Audit Integration Tests
 *
 * Combines three related gaps:
 *
 * GAP: User search by email and mobileNumber — never tested
 * UserSpecification supports email (LIKE) and mobileNumber (LIKE) filters.
 * Only name and isActive were covered in UserSelfServiceIntegrationTest.
 * - Admin searches by email → filtered results
 * - Admin searches by mobileNumber → filtered results
 * - Combined filters (email + isActive) → correct intersection
 *
 * GAP: GET /api/users/{nonExistentId} → never tested
 * getUserById throws RuntimeException("User not found with id: ...") → 500
 * This is a known gap — correct behavior should be 404.
 * Documenting current behavior as a regression anchor.
 *
 * GAP: SEARCH_KYC audit log — never asserted
 * searchKycRequests() calls auditLogService.logAction("SEARCH_KYC",
 * ...) @Async.
 * AuditTrailJourneyTest covers SUBMIT/VIEW_STATUS/VIEW_HISTORY but not
 * SEARCH_KYC.
 * This test verifies the audit entry is created after an admin KYC search.
 */
public class UserQueryAndAuditIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private KycRequestRepository kycRequestRepository;

    @Autowired
    private KycDocumentRepository kycDocumentRepository;

    @Autowired
    private UserRepository userRepository;

    private String adminToken() throws Exception {
        return loginAndGetToken("admin@kyc.com", "Password@123");
    }

    /**
     * Registers a user with a known email and mobile under the default tenant.
     * Returns their userId.
     */
    private Long registerUser(String name, String email, String mobile) throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                .header("X-Tenant-ID", "default")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","email":"%s","password":"Password@123",
                         "mobileNumber":"%s","dob":"1994-05-22"}
                        """.formatted(name, email, mobile)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asLong();
    }

    /**
     * Seeds a KycRequest + KycDocument for the given user.
     */
    private void seedKycRequest(Long userId, String tenantId, KycStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AssertionError("User not found: " + userId));

        KycRequest request = kycRequestRepository.save(KycRequest.builder()
                .user(user)
                .documentType(DocumentType.PAN.name())
                .status(status.name())
                .tenantId(tenantId)
                .attemptNumber(1)
                .submittedAt(LocalDateTime.now().minusHours(1))
                .build());

        kycDocumentRepository.save(KycDocument.builder()
                .kycRequest(request)
                .tenantId(tenantId)
                .documentType(DocumentType.PAN.name())
                .documentNumber("AUDIT123X")
                .documentPath("uploads/audit-doc.jpg")
                .documentHash("aabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccdd")
                .mimeType("image/jpeg")
                .fileSize(40960L)
                .encrypted(true)
                .uploadedAt(LocalDateTime.now().minusHours(1))
                .build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // User search by email
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void admin_searchUsersByEmail_returnsMatchingUsers() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 6);
        String uniqueEmail = "emailsearch." + uid + "@example.com";
        String mobile = String.format("8%09d", Math.abs(uid.hashCode()) % 1_000_000_000L);
        registerUser("Email Search User", uniqueEmail, mobile);

        // Search by partial email — UserSpecification uses LIKE %email%
        MvcResult result = mockMvc.perform(get("/api/users/search")
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Tenant-ID", "default")
                .param("email", "emailsearch." + uid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

        var content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        assertEquals(1, content.size(), "Exactly one user should match the email filter");
        assertEquals(uniqueEmail, content.get(0).get("email").asText(),
                "Returned user's email must match search term");
    }

    @Test
    void admin_searchUsersByEmail_noMatch_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/users/search")
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Tenant-ID", "default")
                .param("email", "absolutely_nonexistent_email_xyz123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // User search by mobileNumber
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void admin_searchUsersByMobile_returnsMatchingUsers() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 6);
        String email = "mobilesearch." + uid + "@example.com";
        // Use a distinctive mobile prefix to avoid matching other test users
        String mobile = "5555" + String.format("%06d", Math.abs(uid.hashCode()) % 1_000_000L);
        registerUser("Mobile Search User", email, mobile);

        MvcResult result = mockMvc.perform(get("/api/users/search")
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Tenant-ID", "default")
                .param("mobileNumber", mobile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

        var content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        assertEquals(1, content.size(), "Exactly one user should match the mobile filter");
        assertEquals(mobile, content.get(0).get("mobileNumber").asText(),
                "Returned user's mobile must match search term");
    }

    @Test
    void admin_searchUsers_combinedEmailAndIsActive_returnsIntersection() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 6);
        String email = "combo." + uid + "@example.com";
        String mobile = String.format("4%09d", Math.abs(uid.hashCode()) % 1_000_000_000L);
        registerUser("Combo User", email, mobile);

        // Search for the user by email AND isActive=true — they were just registered so
        // active
        MvcResult result = mockMvc.perform(get("/api/users/search")
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Tenant-ID", "default")
                .param("email", "combo." + uid)
                .param("isActive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

        var content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        assertEquals(1, content.size(), "Exactly one active user should match");
        assertTrue(content.get(0).get("isActive").asBoolean(),
                "Returned user must be active");

        // Same email but isActive=false → 0 results (user is still active)
        mockMvc.perform(get("/api/users/search")
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Tenant-ID", "default")
                .param("email", "combo." + uid)
                .param("isActive", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GET /api/users/{nonExistentId} → 500
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void admin_getNonExistentUser_returns500() throws Exception {
        // @PreAuthorize("@securityService.canAccessUser(#userId)") on getUserById:
        // - ROLE_TENANT_ADMIN: canAccessUser() loads the user to check same-tenant
        // scoping.
        // For a non-existent id it returns false -> AuthorizationDeniedException ->
        // 403.
        // The service layer is never reached.
        // - ROLE_SUPER_ADMIN: canAccessUser() returns true immediately without loading
        // the user.
        // The request reaches getUserById() which throws RuntimeException -> 500.
        // Must use superadmin to exercise the 500 path.
        // Known gap: should be 404. This test anchors current behavior.
        mockMvc.perform(get("/api/users/99999")
                .header("Authorization", "Bearer " + loginAndGetToken("superadmin@kyc.com", "SuperAdmin@123")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("User not found with id: 99999"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEARCH_KYC audit log
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void admin_searchKyc_createsSearchKycAuditLog() throws Exception {
        // Setup: register a user and seed a KYC request
        String uid = UUID.randomUUID().toString().substring(0, 6);
        String email = "auditkyc." + uid + "@example.com";
        String mobile = String.format("3%09d", Math.abs(uid.hashCode()) % 1_000_000_000L);
        Long userId = registerUser("Audit KYC User", email, mobile);
        seedKycRequest(userId, "default", KycStatus.SUBMITTED);

        String adminEmail = "admin@kyc.com";
        long beforeCount = auditLogRepository.findByPerformedBy(adminEmail)
                .stream()
                .filter(log -> "SEARCH_KYC".equals(log.getAction()))
                .count();

        // Perform the search
        mockMvc.perform(get("/api/kyc/search")
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Tenant-ID", "default")
                .param("userId", String.valueOf(userId)))
                .andExpect(status().isOk());

        // AuditLogService.logAction is @Async — give it time to persist
        Thread.sleep(1000);

        long afterCount = auditLogRepository.findByPerformedBy(adminEmail)
                .stream()
                .filter(log -> "SEARCH_KYC".equals(log.getAction()))
                .count();

        assertTrue(afterCount > beforeCount,
                "A SEARCH_KYC audit log entry must be created when admin searches KYC requests. " +
                        "Before: " + beforeCount + ", After: " + afterCount);
    }

    @Test
    void admin_searchKyc_auditLog_containsSearchCriteria() throws Exception {
        // When a userId filter is passed, the audit log newValue map must include it
        String uid = UUID.randomUUID().toString().substring(0, 6);
        String email = "auditcrit." + uid + "@example.com";
        String mobile = String.format("2%09d", Math.abs(uid.hashCode()) % 1_000_000_000L);
        Long userId = registerUser("Audit Criteria User", email, mobile);
        seedKycRequest(userId, "default", KycStatus.FAILED);

        mockMvc.perform(get("/api/kyc/search")
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Tenant-ID", "default")
                .param("userId", String.valueOf(userId))
                .param("status", "FAILED"))
                .andExpect(status().isOk());

        // Wait for async audit write
        Thread.sleep(1000);

        // Find the most recent SEARCH_KYC log by the admin
        boolean found = auditLogRepository.findByPerformedBy("admin@kyc.com")
                .stream()
                .filter(log -> "SEARCH_KYC".equals(log.getAction()))
                .anyMatch(log -> {
                    if (log.getNewValue() == null)
                        return false;
                    // newValue map contains the search criteria passed to searchKycRequests()
                    Object loggedUserId = log.getNewValue().get("userId");
                    Object loggedStatus = log.getNewValue().get("status");
                    return loggedUserId != null && loggedStatus != null
                            && loggedUserId.toString().equals(String.valueOf(userId))
                            && "FAILED".equals(loggedStatus.toString());
                });

        assertTrue(found,
                "SEARCH_KYC audit log must record the search criteria (userId + status) in newValue");
    }
}
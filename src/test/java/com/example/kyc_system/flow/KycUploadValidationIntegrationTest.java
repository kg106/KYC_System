package com.example.kyc_system.flow;

import com.example.kyc_system.BaseIntegrationTest;
import com.example.kyc_system.entity.KycDocument;
import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.entity.KycVerificationResult;
import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.KycDocumentRepository;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.repository.KycVerificationResultRepository;
import com.example.kyc_system.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ============================================================
 * KYC UPLOAD VALIDATION — GAPS #2 AND #3
 * ============================================================
 *
 * GAP #2 — Empty file upload → 400
 * ─────────────────────────────────
 * KycFileValidator.validate() has two distinct checks:
 * (a) file == null || file.isEmpty() → throws "File is empty"
 * (b) contentType not in allowedTypes → throws "Invalid file type: ..."
 *
 * The existing KycBusinessRulesIntegrationTest already covers (b).
 * This test covers (a) — the completely MISSING empty-file path.
 *
 * Note: KycFileValidator is called from KycDocumentServiceImpl.save(),
 * which is called AFTER createOrReuse() in the orchestrator. However,
 * the production fix (Session 1) moved fileValidator.validate(file) to
 * be the FIRST call in KycOrchestrationService.submitKyc(), so the
 * empty-file rejection now happens before any DB row is created.
 * This means: empty file → 400, NO stuck SUBMITTED rows left behind.
 *
 * GAP #3 — Already-VERIFIED document resubmit → 400
 * ───────────────────────────────────────────────────
 * KycOrchestrationService.submitKyc() calls:
 * if (documentService.isVerified(userId, documentType, documentNumber))
 * throw RuntimeException("Your PAN is already verified...")
 *
 * isVerified() queries:
 * WHERE user_id=? AND document_type=? AND document_number=? AND
 * status='VERIFIED'
 *
 * The only way to get a VERIFIED row in integration tests (without real OCR)
 * is to insert it directly via the repository — which is exactly what we do.
 * This is a legitimate pattern: we seed the DB state we need to test the
 * guard condition, without relying on the async OCR pipeline completing.
 *
 * SCENARIOS COVERED:
 * 1. Upload with empty file (0 bytes) → 400
 * 2. Upload with null content type → 400
 * 3. Empty file leaves NO stuck SUBMITTED row in DB
 * 4. Already-VERIFIED same doc number → 400 "already verified"
 * 5. Same user, same doc type, DIFFERENT doc number → allowed (202)
 * 6. Same user, different doc type, VERIFIED → allowed (202)
 * ============================================================
 */
public class KycUploadValidationIntegrationTest extends BaseIntegrationTest {

        private static final String TENANT = "default";

        @Autowired
        private KycRequestRepository kycRequestRepository;

        @Autowired
        private KycDocumentRepository kycDocumentRepository;

        @Autowired
        private KycVerificationResultRepository kycVerificationResultRepository;

        @Autowired
        private UserRepository userRepository;

        // ═══════════════════════════════════════════════════════════════════════════
        // GAP #2 — EMPTY FILE VALIDATION
        // ═══════════════════════════════════════════════════════════════════════════

        // ── Scenario 1: Upload with 0-byte file → 400 ────────────────────────────

        @Test
        void shouldReject0ByteEmptyFile() throws Exception {
                String uniqueId = UUID.randomUUID().toString().substring(0, 8);
                String email = "empty.file." + uniqueId + "@example.com";
                String mobile = String.format("6%09d", Math.abs(email.hashCode()) % 1_000_000_000L);

                MvcResult reg = mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", TENANT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Empty File User","email":"%s","password":"EmptyFile@123",
                                                 "mobileNumber":"%s","dob":"1992-06-15"}
                                                """.formatted(email, mobile)))
                                .andExpect(status().isCreated())
                                .andReturn();

                Long userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asLong();
                String token = loginAndGetToken(email, "EmptyFile@123");

                // MockMultipartFile with new byte[0] → file.isEmpty() == true
                // KycFileValidator.validate() → throws IllegalArgumentException("File is
                // empty")
                // KycController catches Exception → ResponseEntity.badRequest()
                MockMultipartFile emptyFile = new MockMultipartFile(
                                "file", "empty.jpg", "image/jpeg", new byte[0]);

                MvcResult result = mockMvc.perform(multipart("/api/kyc/upload")
                                .file(emptyFile)
                                .param("userId", userId.toString())
                                .param("documentType", "PAN")
                                .param("documentNumber", "EMPTY1234F")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Tenant-ID", TENANT))
                                .andExpect(status().isBadRequest())
                                .andReturn();

                // Error message must mention the empty file
                String body = result.getResponse().getContentAsString();
                assertTrue(
                                body.contains("File is empty") || body.contains("empty"),
                                "Response must explain the file is empty. Got: " + body);
        }

        // ── Scenario 2: Upload with null content type → 400 ─────────────────────
        //
        // Some clients may omit Content-Type for the file part.
        // KycFileValidator checks: contentType == null → invalid.

        @Test
        void shouldRejectFileWithNullContentType() throws Exception {
                String uniqueId = UUID.randomUUID().toString().substring(0, 8);
                String email = "null.ct." + uniqueId + "@example.com";
                String mobile = String.format("6%09d", Math.abs(email.hashCode()) % 1_000_000_000L);

                MvcResult reg = mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", TENANT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Null CT User","email":"%s","password":"NullCT@1234",
                                                 "mobileNumber":"%s","dob":"1990-02-20"}
                                                """.formatted(email, mobile)))
                                .andExpect(status().isCreated())
                                .andReturn();

                Long userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asLong();
                String token = loginAndGetToken(email, "NullCT@1234");

                // null content type → KycFileValidator: contentType == null → invalid
                MockMultipartFile nullCtFile = new MockMultipartFile(
                                "file", "doc.jpg", null, "some-content".getBytes());

                mockMvc.perform(multipart("/api/kyc/upload")
                                .file(nullCtFile)
                                .param("userId", userId.toString())
                                .param("documentType", "PAN")
                                .param("documentNumber", "NULLCT123F")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Tenant-ID", TENANT))
                                .andExpect(status().isBadRequest());
        }

        // ── Scenario 3: Empty file leaves NO stuck SUBMITTED row in DB ────────────
        //
        // CRITICAL correctness check from the production fix made in Session 1:
        // fileValidator.validate(file) is now called FIRST in submitKyc(),
        // BEFORE createOrReuse(). This means a rejected empty file must NOT
        // leave a SUBMITTED row in the database.
        //
        // If this test fails, it means the production fix was accidentally reverted
        // and the validator is back to being called AFTER createOrReuse().

        @Test
        void emptyFileRejectionMustNotLeaveStuckSubmittedRowInDatabase() throws Exception {
                String uniqueId = UUID.randomUUID().toString().substring(0, 8);
                String email = "no.stuck." + uniqueId + "@example.com";
                String mobile = String.format("6%09d", Math.abs(email.hashCode()) % 1_000_000_000L);

                MvcResult reg = mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", TENANT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"No Stuck User","email":"%s","password":"NoStuck@123",
                                                 "mobileNumber":"%s","dob":"1988-09-01"}
                                                """.formatted(email, mobile)))
                                .andExpect(status().isCreated())
                                .andReturn();

                Long userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asLong();
                String token = loginAndGetToken(email, "NoStuck@123");

                // Count KYC rows BEFORE the failed upload
                long rowsBefore = kycRequestRepository.findByUserId(userId).size();

                // Attempt upload with empty file → will be rejected
                mockMvc.perform(multipart("/api/kyc/upload")
                                .file(new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]))
                                .param("userId", userId.toString())
                                .param("documentType", "PAN")
                                .param("documentNumber", "STUCK1234F")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Tenant-ID", TENANT))
                                .andExpect(status().isBadRequest());

                // Count KYC rows AFTER the failed upload
                long rowsAfter = kycRequestRepository.findByUserId(userId).size();

                // MUST be equal — no row should have been created
                assertEquals(rowsBefore, rowsAfter,
                                "Empty file rejection must NOT create a KYC row. " +
                                                "Found " + (rowsAfter - rowsBefore) + " unexpected row(s). " +
                                                "This means fileValidator.validate() is being called AFTER " +
                                                "createOrReuse() — the production fix may have been reverted.");
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // GAP #3 — ALREADY-VERIFIED DOCUMENT RESUBMIT BLOCK
        // ═══════════════════════════════════════════════════════════════════════════

        // ── Scenario 4: Resubmit same doc number that is already VERIFIED → 400 ──
        //
        // Strategy: We seed the VERIFIED state directly via repositories.
        // This bypasses OCR (which requires real Tesseract) and lets us test
        // the guard condition in isolation.
        //
        // isVerified() checks:
        // WHERE user_id=? AND document_type='PAN' AND document_number='VERIFIED1F' AND
        // status='VERIFIED'

        @Test
        void shouldBlockResubmissionOfAlreadyVerifiedDocument() throws Exception {
                String uniqueId = UUID.randomUUID().toString().substring(0, 8);
                String email = "already.verified." + uniqueId + "@example.com";
                String mobile = String.format("9%09d", Math.abs(email.hashCode()) % 1_000_000_000L);
                String docNumber = "VERIFIED1F";

                // Step 1: Register user
                MvcResult reg = mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", TENANT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Already Verified","email":"%s","password":"Verified@123",
                                                 "mobileNumber":"%s","dob":"1985-12-25"}
                                                """.formatted(email, mobile)))
                                .andExpect(status().isCreated())
                                .andReturn();

                Long userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asLong();
                String token = loginAndGetToken(email, "Verified@123");

                // Step 2: Seed a VERIFIED KYC row directly in DB
                // (bypasses OCR — we directly set the final state we need)
                com.example.kyc_system.entity.User user = userRepository.findById(userId)
                                .orElseThrow(() -> new AssertionError("User not found: " + userId));

                KycRequest verifiedRequest = KycRequest.builder()
                                .user(user)
                                .documentType(DocumentType.PAN.name())
                                .status(KycStatus.VERIFIED.name())
                                .attemptNumber(1)
                                .tenantId(TENANT)
                                .submittedAt(LocalDateTime.now().minusHours(2))
                                .processingStartedAt(LocalDateTime.now().minusHours(1))
                                .completedAt(LocalDateTime.now().minusMinutes(30))
                                .build();
                verifiedRequest = kycRequestRepository.save(verifiedRequest);

                KycDocument verifiedDoc = KycDocument.builder()
                                .kycRequest(verifiedRequest)
                                .tenantId(TENANT)
                                .documentType(DocumentType.PAN.name())
                                .documentNumber(docNumber) // ← same doc number we'll try to resubmit
                                .documentPath("uploads/test-pan.jpg")
                                .documentHash("abc123def456abc123def456abc123def456abc123def456abc123def456abc1")
                                .mimeType("image/jpeg")
                                .fileSize(102400L)
                                .encrypted(true)
                                .uploadedAt(LocalDateTime.now().minusHours(2))
                                .build();
                kycDocumentRepository.save(verifiedDoc);

                // Step 3: Try to upload the SAME document number again → must be blocked
                // isVerified() → finds the row → throws RuntimeException("already verified")
                // KycController catches → ResponseEntity.badRequest() → 400
                MvcResult result = mockMvc.perform(multipart("/api/kyc/upload")
                                .file(new MockMultipartFile("file", "pan.jpg", "image/jpeg", "bytes".getBytes()))
                                .param("userId", userId.toString())
                                .param("documentType", "PAN")
                                .param("documentNumber", docNumber) // ← same doc number
                                .header("Authorization", "Bearer " + token)
                                .header("X-Tenant-ID", TENANT))
                                .andExpect(status().isBadRequest())
                                .andReturn();

                String body = result.getResponse().getContentAsString();
                assertTrue(
                                body.contains("already verified"),
                                "Response must say 'already verified'. Got: " + body);
        }

        // ── Scenario 5: Same user, same doc type, DIFFERENT doc number → allowed ──
        //
        // The isVerified() check is keyed on (userId, documentType, documentNumber).
        // If the user submits a DIFFERENT document number (e.g., correcting a typo),
        // it must NOT be blocked by the VERIFIED check.

        @Test
        void shouldAllowResubmissionWithDifferentDocumentNumber() throws Exception {
                String uniqueId = UUID.randomUUID().toString().substring(0, 8);
                String email = "diff.docnum." + uniqueId + "@example.com";
                String mobile = String.format("9%09d", Math.abs(email.hashCode()) % 1_000_000_000L);
                String verifiedDocNumber = "VERIFPAN1A";
                String newDocNumber = "NEWPAN2222"; // different number

                // Register
                MvcResult reg = mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", TENANT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Diff Doc Num","email":"%s","password":"DiffDoc@123",
                                                 "mobileNumber":"%s","dob":"1993-03-03"}
                                                """.formatted(email, mobile)))
                                .andExpect(status().isCreated())
                                .andReturn();

                Long userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asLong();
                String token = loginAndGetToken(email, "DiffDoc@123");

                // Seed a VERIFIED row for verifiedDocNumber
                com.example.kyc_system.entity.User user = userRepository.findById(userId)
                                .orElseThrow(() -> new AssertionError("User not found: " + userId));

                KycRequest verifiedRequest = KycRequest.builder()
                                .user(user)
                                .documentType(DocumentType.PAN.name())
                                .status(KycStatus.VERIFIED.name())
                                .attemptNumber(1)
                                .tenantId(TENANT)
                                .submittedAt(LocalDateTime.now().minusHours(2))
                                .completedAt(LocalDateTime.now().minusHours(1))
                                .build();
                verifiedRequest = kycRequestRepository.save(verifiedRequest);

                KycDocument verifiedDoc = KycDocument.builder()
                                .kycRequest(verifiedRequest)
                                .tenantId(TENANT)
                                .documentType(DocumentType.PAN.name())
                                .documentNumber(verifiedDocNumber)
                                .documentPath("uploads/test-pan.jpg")
                                .documentHash("abc123def456abc123def456abc123def456abc123def456abc123def456abc1")
                                .mimeType("image/jpeg")
                                .fileSize(102400L)
                                .encrypted(true)
                                .uploadedAt(LocalDateTime.now().minusHours(2))
                                .build();
                kycDocumentRepository.save(verifiedDoc);

                // Upload with a DIFFERENT doc number → isVerified() returns false → allowed
                // createOrReuse() will create a new SUBMITTED row for PAN
                mockMvc.perform(multipart("/api/kyc/upload")
                                .file(new MockMultipartFile("file", "pan.jpg", "image/jpeg", "bytes".getBytes()))
                                .param("userId", userId.toString())
                                .param("documentType", "PAN")
                                .param("documentNumber", newDocNumber) // ← different number
                                .header("Authorization", "Bearer " + token)
                                .header("X-Tenant-ID", TENANT))
                                .andExpect(status().isAccepted());
        }

        // ── Scenario 6: Same user, different doc TYPE that is VERIFIED → allowed ──
        //
        // isVerified() is scoped to (userId, documentType, documentNumber).
        // A VERIFIED PAN must NOT block submitting AADHAAR.

        @Test
        void shouldAllowDifferentDocumentTypeEvenIfAnotherTypeIsVerified() throws Exception {
                String uniqueId = UUID.randomUUID().toString().substring(0, 8);
                String email = "diff.doctype." + uniqueId + "@example.com";
                String mobile = String.format("9%09d", Math.abs(email.hashCode()) % 1_000_000_000L);

                // Register
                MvcResult reg = mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", TENANT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Diff Doc Type","email":"%s","password":"DiffType@123",
                                                 "mobileNumber":"%s","dob":"1991-07-07"}
                                                """.formatted(email, mobile)))
                                .andExpect(status().isCreated())
                                .andReturn();

                Long userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asLong();
                String token = loginAndGetToken(email, "DiffType@123");

                // Seed a VERIFIED PAN row
                com.example.kyc_system.entity.User user = userRepository.findById(userId)
                                .orElseThrow(() -> new AssertionError("User not found: " + userId));

                KycRequest verifiedPan = KycRequest.builder()
                                .user(user)
                                .documentType(DocumentType.PAN.name())
                                .status(KycStatus.VERIFIED.name())
                                .attemptNumber(1)
                                .tenantId(TENANT)
                                .submittedAt(LocalDateTime.now().minusHours(3))
                                .completedAt(LocalDateTime.now().minusHours(2))
                                .build();
                verifiedPan = kycRequestRepository.save(verifiedPan);

                kycDocumentRepository.save(KycDocument.builder()
                                .kycRequest(verifiedPan)
                                .tenantId(TENANT)
                                .documentType(DocumentType.PAN.name())
                                .documentNumber("VERIFIEDPAN1")
                                .documentPath("uploads/pan.jpg")
                                .documentHash("abc123def456abc123def456abc123def456abc123def456abc123def456abc1")
                                .mimeType("image/jpeg")
                                .fileSize(102400L)
                                .encrypted(true)
                                .uploadedAt(LocalDateTime.now().minusHours(3))
                                .build());

                // Now upload AADHAAR → isVerified(userId, AADHAAR, "1234-5678-9012") = false
                // Must be accepted regardless of PAN being VERIFIED
                mockMvc.perform(multipart("/api/kyc/upload")
                                .file(new MockMultipartFile("file", "aadhaar.jpg", "image/jpeg", "bytes".getBytes()))
                                .param("userId", userId.toString())
                                .param("documentType", "AADHAAR")
                                .param("documentNumber", "1234-5678-9012")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Tenant-ID", TENANT))
                                .andExpect(status().isAccepted());
        }
}
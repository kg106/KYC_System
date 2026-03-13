package com.example.kyc_system.service;

import com.example.kyc_system.entity.AuditLog;
import com.example.kyc_system.service.impl.*;
import com.example.kyc_system.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditLogServiceImpl.
 *
 * Key behaviors tested:
 * - logAction(String details) maps to correct AuditLog fields
 * - logAction(Map details) persists full map
 * - Sensitive keys are masked before persistence
 * - Exceptions during save are swallowed (not propagated)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogServiceImpl Unit Tests")
class AuditLogServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    // ─── logAction(String details) ─────────────────────────────────────────────

    @Nested
    @DisplayName("logAction(String details)")
    class StringDetailsTests {

        @Test
        @DisplayName("Should persist AuditLog with correct action and entity details")
        void logAction_StringDetails_PersistsCorrectly() throws InterruptedException {
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditLogService.logAction("SUBMIT", "KycRequest", 1L, "Submitted new KYC", "user@example.com");

            // @Async — give it a moment to execute on thread pool
            Thread.sleep(200);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository, atLeastOnce()).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertEquals("SUBMIT", saved.getAction());
            assertEquals("KycRequest", saved.getEntityType());
            assertEquals(1L, saved.getEntityId());
            assertEquals("user@example.com", saved.getPerformedBy());
            assertNotNull(saved.getNewValue());
            assertTrue(saved.getNewValue().containsKey("message"));
            assertEquals("Submitted new KYC", saved.getNewValue().get("message"));
        }

        @Test
        @DisplayName("Should not throw when repository throws — exception is swallowed")
        void logAction_RepositoryThrows_NoExceptionPropagated() {
            when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB error"));

            // Should not propagate to the caller
            assertDoesNotThrow(() -> auditLogService.logAction("UPDATE_STATUS", "KycRequest", 2L, "Updated", "admin"));
        }
    }

    // ─── logAction(Map details) ────────────────────────────────────────────────

    @Nested
    @DisplayName("logAction(Map details)")
    class MapDetailsTests {

        @Test
        @DisplayName("Should persist AuditLog with all non-sensitive map fields intact")
        void logAction_MapDetails_PersistsCorrectly() throws InterruptedException {
            Map<String, Object> details = new HashMap<>();
            details.put("status", "VERIFIED");
            details.put("documentType", "PAN");

            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditLogService.logAction("UPDATE_STATUS", "KycRequest", 5L, details, "admin@test.com");

            Thread.sleep(200);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository, atLeastOnce()).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertEquals("UPDATE_STATUS", saved.getAction());
            assertEquals("VERIFIED", saved.getNewValue().get("status"));
            assertEquals("PAN", saved.getNewValue().get("documentType"));
        }

        @Test
        @DisplayName("Should mask sensitive keys — password, token, aadhaar, pan, dob, documentnumber")
        void logAction_SensitiveKeys_AreMasked() throws InterruptedException {
            Map<String, Object> details = new HashMap<>();
            details.put("password", "secret123");
            details.put("token", "eyJhbGci...");
            details.put("aadhaar", "1234 5678 9012");
            details.put("pan", "ABCDE1234F");
            details.put("dob", "1990-01-01");
            details.put("documentnumber", "DOC12345");
            details.put("action", "LOGIN"); // non-sensitive, should remain

            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditLogService.logAction("LOGIN", "User", 1L, details, "system");

            Thread.sleep(200);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository, atLeastOnce()).save(captor.capture());

            Map<String, Object> newValue = captor.getValue().getNewValue();

            // Sensitive fields must be masked (not equal to original)
            assertNotEquals("secret123", newValue.get("password"), "password must be masked");
            assertNotEquals("eyJhbGci...", newValue.get("token"), "token must be masked");
            assertNotEquals("1234 5678 9012", newValue.get("aadhaar"), "aadhaar must be masked");
            assertNotEquals("ABCDE1234F", newValue.get("pan"), "pan must be masked");
            assertNotEquals("1990-01-01", newValue.get("dob"), "dob must be masked");
            assertNotEquals("DOC12345", newValue.get("documentnumber"), "documentnumber must be masked");

            // Non-sensitive field stays intact
            assertEquals("LOGIN", newValue.get("action"), "non-sensitive field must not be masked");
        }

        @Test
        @DisplayName("Should handle empty details map without throwing")
        void logAction_EmptyDetailsMap_NoException() {
            when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertDoesNotThrow(() -> auditLogService.logAction("SUBMIT", "KycRequest", 1L, new HashMap<>(), "user"));
        }

        @Test
        @DisplayName("Should not throw when repository throws during map-based log")
        void logAction_MapDetails_RepositoryThrows_ExceptionSwallowed() {
            when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB unavailable"));

            assertDoesNotThrow(() -> auditLogService.logAction("DELETE", "User", 3L,
                    Map.of("reason", "user request"), "admin"));
        }
    }

    // ─── Sensitive Key Coverage ────────────────────────────────────────────────

    @Nested
    @DisplayName("Sensitive key set coverage")
    class SensitiveKeyTests {

        @Test
        @DisplayName("Key matching is case-insensitive — 'Authorization' should be masked")
        void logAction_CaseInsensitiveSensitiveKey_IsMasked() throws InterruptedException {
            Map<String, Object> details = new HashMap<>();
            details.put("authorization", "Bearer token-value");
            details.put("Secret", "mySecret");

            when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            auditLogService.logAction("REQUEST", "API", 1L, details, "gateway");
            Thread.sleep(200);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository, atLeastOnce()).save(captor.capture());

            Map<String, Object> newValue = captor.getValue().getNewValue();
            assertNotEquals("Bearer token-value", newValue.get("authorization"));
        }
    }
}
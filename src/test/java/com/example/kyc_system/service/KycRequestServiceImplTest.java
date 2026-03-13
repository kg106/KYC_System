package com.example.kyc_system.service;

import com.example.kyc_system.context.TenantContext;
import com.example.kyc_system.entity.*;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.repository.TenantRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KycRequestServiceImpl Unit Tests")
class KycRequestServiceImplTest {

    @Mock
    private KycRequestRepository repository;
    @Mock
    private UserService userService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private KycRequestServiceImpl requestService;

    private User mockUser;
    private Tenant mockTenant;
    private KycRequest existingRequest;

    @BeforeEach
    void setUp() {
        mockUser = User.builder().id(1L).name("John Doe").build();

        mockTenant = new Tenant();
        mockTenant.setTenantId("default");
        mockTenant.setMaxDailyAttempts(5);

        existingRequest = KycRequest.builder()
                .id(10L)
                .user(mockUser)
                .documentType("PAN")
                .status(KycStatus.FAILED.name())
                .attemptNumber(1)
                .tenantId("default")
                .build();

        // Mock SecurityContext for audit log
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("system");
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── createOrReuse() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("createOrReuse()")
    class CreateOrReuseTests {

        @Test
        @DisplayName("Should create new request when no existing request found")
        void createOrReuse_NoExisting_CreatesNewRequest() {
            try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
                tenantCtx.when(TenantContext::getTenant).thenReturn("default");

                when(tenantRepository.findByTenantId("default")).thenReturn(Optional.of(mockTenant));
                when(repository.sumAttemptNumberByUserIdAndTenantIdAndSubmittedAtAfter(
                        anyLong(), anyString(), any())).thenReturn(0L);
                when(repository.findTopByUserIdAndDocumentTypeAndTenantIdOrderByCreatedAtDesc(
                        1L, "PAN", "default")).thenReturn(Optional.empty());
                when(userService.getActiveUser(1L)).thenReturn(mockUser);
                when(repository.save(any(KycRequest.class))).thenAnswer(i -> {
                    KycRequest r = i.getArgument(0);
                    r.setId(99L);
                    return r;
                });

                KycRequest result = requestService.createOrReuse(1L, "PAN");

                assertNotNull(result);
                assertEquals(KycStatus.SUBMITTED.name(), result.getStatus());
                assertEquals(1, result.getAttemptNumber());
                assertEquals("default", result.getTenantId());
                verify(repository).save(any(KycRequest.class));
            }
        }

        @Test
        @DisplayName("Should re-use and increment attempt number on FAILED request")
        void createOrReuse_ExistingFailed_ReusesAndIncrementsAttempt() {
            try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
                tenantCtx.when(TenantContext::getTenant).thenReturn("default");

                when(tenantRepository.findByTenantId("default")).thenReturn(Optional.of(mockTenant));
                when(repository.sumAttemptNumberByUserIdAndTenantIdAndSubmittedAtAfter(
                        anyLong(), anyString(), any())).thenReturn(1L);
                when(repository.findTopByUserIdAndDocumentTypeAndTenantIdOrderByCreatedAtDesc(
                        1L, "PAN", "default")).thenReturn(Optional.of(existingRequest));

                KycRequest result = requestService.createOrReuse(1L, "PAN");

                assertEquals(KycStatus.SUBMITTED.name(), result.getStatus());
                assertEquals(2, result.getAttemptNumber()); // incremented
                verify(repository, never()).save(any()); // re-use = no new save
            }
        }

        @Test
        @DisplayName("Should throw when existing request is still SUBMITTED/PROCESSING")
        void createOrReuse_ExistingSubmitted_ThrowsException() {
            existingRequest.setStatus(KycStatus.SUBMITTED.name());

            try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
                tenantCtx.when(TenantContext::getTenant).thenReturn("default");

                when(tenantRepository.findByTenantId("default")).thenReturn(Optional.of(mockTenant));
                when(repository.sumAttemptNumberByUserIdAndTenantIdAndSubmittedAtAfter(
                        anyLong(), anyString(), any())).thenReturn(1L);
                when(repository.findTopByUserIdAndDocumentTypeAndTenantIdOrderByCreatedAtDesc(
                        1L, "PAN", "default")).thenReturn(Optional.of(existingRequest));

                RuntimeException ex = assertThrows(RuntimeException.class,
                        () -> requestService.createOrReuse(1L, "PAN"));

                assertTrue(ex.getMessage().contains("can be processed at a time"));
            }
        }

        @Test
        @DisplayName("Should throw when existing request is still PROCESSING")
        void createOrReuse_ExistingProcessing_ThrowsException() {
            existingRequest.setStatus(KycStatus.PROCESSING.name());

            try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
                tenantCtx.when(TenantContext::getTenant).thenReturn("default");

                when(tenantRepository.findByTenantId("default")).thenReturn(Optional.of(mockTenant));
                when(repository.sumAttemptNumberByUserIdAndTenantIdAndSubmittedAtAfter(
                        anyLong(), anyString(), any())).thenReturn(1L);
                when(repository.findTopByUserIdAndDocumentTypeAndTenantIdOrderByCreatedAtDesc(
                        1L, "PAN", "default")).thenReturn(Optional.of(existingRequest));

                assertThrows(RuntimeException.class, () -> requestService.createOrReuse(1L, "PAN"));
            }
        }

        @Test
        @DisplayName("Should throw when daily attempt limit is reached")
        void createOrReuse_DailyLimitReached_ThrowsException() {
            try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
                tenantCtx.when(TenantContext::getTenant).thenReturn("default");

                when(tenantRepository.findByTenantId("default")).thenReturn(Optional.of(mockTenant));
                // Return attempts >= maxDailyAttempts (5)
                when(repository.sumAttemptNumberByUserIdAndTenantIdAndSubmittedAtAfter(
                        anyLong(), anyString(), any())).thenReturn(5L);

                RuntimeException ex = assertThrows(RuntimeException.class,
                        () -> requestService.createOrReuse(1L, "PAN"));

                assertTrue(ex.getMessage().contains("Daily KYC attempt limit"));
            }
        }

        @Test
        @DisplayName("Should throw when tenant not found")
        void createOrReuse_TenantNotFound_ThrowsException() {
            try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
                tenantCtx.when(TenantContext::getTenant).thenReturn("unknown-tenant");

                when(tenantRepository.findByTenantId("unknown-tenant")).thenReturn(Optional.empty());

                RuntimeException ex = assertThrows(RuntimeException.class,
                        () -> requestService.createOrReuse(1L, "PAN"));

                assertTrue(ex.getMessage().contains("Tenant not found"));
            }
        }
    }

    // ─── updateStatus() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatusTests {

        @Test
        @DisplayName("Should update status and write audit log")
        void updateStatus_ExistingRequest_UpdatesStatus() {
            KycRequest req = KycRequest.builder().id(10L).status(KycStatus.SUBMITTED.name()).build();
            when(repository.findById(10L)).thenReturn(Optional.of(req));

            requestService.updateStatus(10L, KycStatus.VERIFIED);

            assertEquals(KycStatus.VERIFIED.name(), req.getStatus());
            verify(auditLogService).logAction(eq("UPDATE_STATUS"), eq("KycRequest"),
                    eq(10L), anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw RuntimeException when request not found")
        void updateStatus_NotFound_ThrowsException() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> requestService.updateStatus(99L, KycStatus.VERIFIED));

            assertTrue(ex.getMessage().contains("KYC request not found"));
        }
    }

    // ─── getLatestByUser() ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getLatestByUser()")
    class GetLatestByUserTests {

        @Test
        @DisplayName("Should return latest request when one exists")
        void getLatestByUser_RequestExists_ReturnsOptional() {
            try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
                tenantCtx.when(TenantContext::getTenant).thenReturn("default");
                when(repository.findTopByUserIdOrderByCreatedAtDesc(1L))
                        .thenReturn(Optional.of(existingRequest));

                Optional<KycRequest> result = requestService.getLatestByUser(1L);

                assertTrue(result.isPresent());
                assertEquals(10L, result.get().getId());
            }
        }

        @Test
        @DisplayName("Should return empty Optional when no request exists for user")
        void getLatestByUser_NoRequest_ReturnsEmptyOptional() {
            try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
                tenantCtx.when(TenantContext::getTenant).thenReturn("default");
                when(repository.findTopByUserIdOrderByCreatedAtDesc(1L))
                        .thenReturn(Optional.empty());

                Optional<KycRequest> result = requestService.getLatestByUser(1L);

                assertFalse(result.isPresent());
            }
        }
    }

    // ─── getAllByUser() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllByUser()")
    class GetAllByUserTests {

        @Test
        @DisplayName("Should return all requests for user scoped by tenant")
        void getAllByUser_MultipleRequests_ReturnsAll() {
            try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
                tenantCtx.when(TenantContext::getTenant).thenReturn("default");
                List<KycRequest> requests = List.of(
                        KycRequest.builder().id(1L).build(),
                        KycRequest.builder().id(2L).build());
                when(repository.findByUserId(1L)).thenReturn(requests);

                List<KycRequest> result = requestService.getAllByUser(1L);

                assertEquals(2, result.size());
            }
        }

        @Test
        @DisplayName("Should return empty list when user has no requests")
        void getAllByUser_NoRequests_ReturnsEmptyList() {
            try (MockedStatic<TenantContext> tenantCtx = mockStatic(TenantContext.class)) {
                tenantCtx.when(TenantContext::getTenant).thenReturn("default");
                when(repository.findByUserId(1L)).thenReturn(List.of());

                List<KycRequest> result = requestService.getAllByUser(1L);

                assertTrue(result.isEmpty());
            }
        }
    }
}
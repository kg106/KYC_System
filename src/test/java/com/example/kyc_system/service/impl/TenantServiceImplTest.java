package com.example.kyc_system.service.impl;

import com.example.kyc_system.dto.*;
import com.example.kyc_system.entity.*;
import com.example.kyc_system.exception.BusinessException;
import com.example.kyc_system.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TenantServiceImpl Unit Tests")
class TenantServiceImplTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private KycRequestRepository kycRequestRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private TenantServiceImpl tenantService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Should create tenant successfully without admin")
    void createTenant_NoAdmin_Success() {
        TenantCreateDTO dto = TenantCreateDTO.builder()
                .tenantId("test_tenant")
                .name("Test Tenant")
                .email("test@tenant.com")
                .build();

        when(tenantRepository.existsByTenantId("test_tenant")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArguments()[0]);

        TenantDTO result = tenantService.createTenant(dto);

        assertNotNull(result);
        assertEquals("test_tenant", result.getTenantId());
        verify(tenantRepository).save(any(Tenant.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should create tenant and provision admin successfully")
    void createTenant_WithAdmin_Success() {
        TenantCreateDTO dto = TenantCreateDTO.builder()
                .tenantId("test_tenant")
                .name("Test Tenant")
                .email("test@tenant.com")
                .adminEmail("admin@tenant.com")
                .adminPassword("password")
                .build();

        Tenant tenant = Tenant.builder().tenantId("test_tenant").name("Test Tenant").build();
        Role role = Role.builder().name("ROLE_TENANT_ADMIN").build();

        when(tenantRepository.existsByTenantId("test_tenant")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenReturn(User.builder().id(1L).build());
        when(roleRepository.findByName("ROLE_TENANT_ADMIN")).thenReturn(Optional.of(role));

        TenantDTO result = tenantService.createTenant(dto);

        assertNotNull(result);
        verify(userRepository).save(any(User.class));
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    @DisplayName("Should throw exception if tenant ID already exists")
    void createTenant_DuplicateId_ThrowsException() {
        TenantCreateDTO dto = TenantCreateDTO.builder().tenantId("existing").build();
        when(tenantRepository.existsByTenantId("existing")).thenReturn(true);

        assertThrows(BusinessException.class, () -> tenantService.createTenant(dto));
    }

    @Test
    @DisplayName("Should return tenant by ID")
    void getTenant_Found_ReturnsDTO() {
        Tenant tenant = Tenant.builder().tenantId("t1").name("Tenant 1").build();
        when(tenantRepository.findByTenantId("t1")).thenReturn(Optional.of(tenant));

        TenantDTO result = tenantService.getTenant("t1");

        assertNotNull(result);
        assertEquals("t1", result.getTenantId());
    }

    @Test
    @DisplayName("Should throw exception if tenant not found")
    void getTenant_NotFound_ThrowsException() {
        when(tenantRepository.findByTenantId("any")).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> tenantService.getTenant("any"));
    }

    @Test
    @DisplayName("Should return page of tenants")
    void getAllTenants_ReturnsPage() {
        Page<Tenant> page = new PageImpl<>(List.of(Tenant.builder().tenantId("t1").build()));
        when(tenantRepository.findAll(any(PageRequest.class))).thenReturn(page);

        Page<TenantDTO> result = tenantService.getAllTenants(PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Should update tenant fields")
    void updateTenant_Success() {
        Tenant tenant = Tenant.builder().tenantId("t1").name("Old Name").build();
        TenantUpdateDTO dto = TenantUpdateDTO.builder().name("New Name").build();

        when(tenantRepository.findByTenantId("t1")).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArguments()[0]);

        TenantDTO result = tenantService.updateTenant("t1", dto);

        assertEquals("New Name", result.getName());
    }

    @Test
    @DisplayName("Should rotate API key")
    void rotateApiKey_Success() {
        Tenant tenant = Tenant.builder().tenantId("t1").apiKey("old_key").build();
        when(tenantRepository.findByTenantId("t1")).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

        String newKey = tenantService.rotateApiKey("t1");

        assertNotNull(newKey);
        assertNotEquals("old_key", newKey);
        assertTrue(newKey.startsWith("kyc_"));
    }

    @Test
    @DisplayName("Should calculate tenant stats correctly")
    void getStats_Success() {
        Tenant tenant = Tenant.builder().tenantId("t1").name("T1").build();
        when(tenantRepository.findByTenantId("t1")).thenReturn(Optional.of(tenant));
        when(userRepository.countByTenantId("t1")).thenReturn(10L);
        when(kycRequestRepository.countByTenantId("t1")).thenReturn(100L);
        when(kycRequestRepository.countByTenantIdAndStatus("t1", "VERIFIED")).thenReturn(80L);
        when(kycRequestRepository.countByTenantIdAndStatus("t1", "FAILED")).thenReturn(10L);

        TenantStatsDTO stats = tenantService.getStats("t1");

        assertEquals(10L, stats.getTotalUsers());
        assertEquals(100L, stats.getTotalKycRequests());
        assertEquals(80L, stats.getVerified());
        assertEquals(10L, stats.getFailed());
        assertEquals(10L, stats.getPending());
        assertEquals(80.0, stats.getPassRate());
    }
}

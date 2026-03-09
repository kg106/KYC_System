package com.example.kyc_system.service;

import com.example.kyc_system.dto.*;
import com.example.kyc_system.entity.*;
import com.example.kyc_system.exception.BusinessException;
import com.example.kyc_system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final KycRequestRepository kycRequestRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public TenantDTO createTenant(TenantCreateDTO dto) {
        if (tenantRepository.existsByTenantId(dto.getTenantId())) {
            throw new BusinessException(
                    "Tenant ID already exists: " + dto.getTenantId());
        }

        Tenant tenant = Tenant.builder()
                .tenantId(dto.getTenantId())
                .name(dto.getName())
                .email(dto.getEmail())
                .plan("BASIC")
                .isActive(true)
                .maxDailyAttempts(dto.getMaxDailyAttempts() != null ? dto.getMaxDailyAttempts() : 5)
                .allowedDocumentTypes(
                        dto.getAllowedDocumentTypes() != null
                                ? String.join(",", dto.getAllowedDocumentTypes())
                                : "PAN,AADHAAR")
                .apiKey(generateApiKey())
                .build();

        Tenant saved = tenantRepository.save(tenant);
        log.info("Tenant created: {}", saved.getTenantId());

        // Auto-provision tenant admin if credentials provided
        if (dto.getAdminEmail() != null && dto.getAdminPassword() != null) {
            provisionTenantAdmin(saved, dto.getAdminEmail(), dto.getAdminPassword());
        }

        return mapToDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantDTO getTenant(String tenantId) {
        return mapToDTO(getOrThrow(tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TenantDTO> getAllTenants(Pageable pageable) {
        return tenantRepository.findAll(pageable).map(this::mapToDTO);
    }

    @Override
    public TenantDTO updateTenant(String tenantId, TenantUpdateDTO dto) {
        Tenant tenant = getOrThrow(tenantId);

        if (dto.getName() != null)
            tenant.setName(dto.getName());
        if (dto.getEmail() != null)
            tenant.setEmail(dto.getEmail());
        if (dto.getMaxDailyAttempts() != null)
            tenant.setMaxDailyAttempts(dto.getMaxDailyAttempts());
        if (dto.getAllowedDocumentTypes() != null)
            tenant.setAllowedDocumentTypes(String.join(",", dto.getAllowedDocumentTypes()));

        return mapToDTO(tenantRepository.save(tenant));
    }

    @Override
    public boolean setActive(String tenantId, boolean active) {
        Tenant tenant = getOrThrow(tenantId);
        if (tenant.getIsActive() == active) {
            return false;
        }
        tenant.setIsActive(active);
        tenantRepository.save(tenant);
        log.info("Tenant {} set to active={}", tenantId, active);
        return true;
    }

    @Override
    public String rotateApiKey(String tenantId) {
        Tenant tenant = getOrThrow(tenantId);
        String newKey = generateApiKey();
        tenant.setApiKey(newKey);
        tenantRepository.save(tenant);
        log.info("API key rotated for tenant: {}", tenantId);
        return newKey;
    }

    @Override
    @Transactional(readOnly = true)
    public TenantStatsDTO getStats(String tenantId) {
        Tenant tenant = getOrThrow(tenantId);

        long totalUsers = userRepository.countByTenantId(tenantId);
        long totalRequests = kycRequestRepository.countByTenantId(tenantId);
        long verified = kycRequestRepository.countByTenantIdAndStatus(tenantId, "VERIFIED");
        long failed = kycRequestRepository.countByTenantIdAndStatus(tenantId, "FAILED");
        long pending = totalRequests - verified - failed;

        return TenantStatsDTO.builder()
                .tenantId(tenantId)
                .tenantName(tenant.getName())
                .totalUsers(totalUsers)
                .totalKycRequests(totalRequests)
                .verified(verified)
                .failed(failed)
                .pending(Math.max(pending, 0))
                .passRate(totalRequests > 0 ? (double) verified / totalRequests * 100 : 0)
                .build();
    }

    // ─── Private Helpers ──────────────────────────────────────────

    private void provisionTenantAdmin(Tenant tenant, String adminEmail, String adminPassword) {
        // Generate a unique dummy mobile number for tenant admin to avoid conflict
        String mobile = String.format("%010d", Math.abs((tenant.getTenantId() + "admin").hashCode()) % 10000000000L);

        User admin = User.builder()
                .name(tenant.getName() + " Admin")
                .email(adminEmail)
                .mobileNumber(mobile)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .tenantId(tenant.getTenantId())
                .isActive(true)
                .build();

        User savedAdmin = userRepository.save(admin);

        roleRepository.findByName("ROLE_TENANT_ADMIN").ifPresent(role -> userRoleRepository.save(UserRole.builder()
                .user(savedAdmin)
                .role(role)
                .build()));

        log.info("Tenant admin provisioned for tenant: {}", tenant.getTenantId());
    }

    private String generateApiKey() {
        return "kyc_" + UUID.randomUUID().toString().replace("-", "");
    }

    private Tenant getOrThrow(String tenantId) {
        return tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));
    }

    private TenantDTO mapToDTO(Tenant tenant) {
        return TenantDTO.builder()
                .id(tenant.getId())
                .tenantId(tenant.getTenantId())
                .name(tenant.getName())
                .email(tenant.getEmail())
                .plan(tenant.getPlan())
                .isActive(tenant.getIsActive())
                .maxDailyAttempts(tenant.getMaxDailyAttempts())
                .allowedDocumentTypes(tenant.getAllowedDocumentTypes())
                .apiKey(tenant.getApiKey())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }
}
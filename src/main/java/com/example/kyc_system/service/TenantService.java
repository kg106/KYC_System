package com.example.kyc_system.service;

import com.example.kyc_system.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TenantService {

    TenantDTO createTenant(TenantCreateDTO dto);

    TenantDTO getTenant(String tenantId);

    Page<TenantDTO> getAllTenants(Pageable pageable);

    TenantDTO updateTenant(String tenantId, TenantUpdateDTO dto);

    boolean setActive(String tenantId, boolean active);

    String rotateApiKey(String tenantId);

    TenantStatsDTO getStats(String tenantId);
}
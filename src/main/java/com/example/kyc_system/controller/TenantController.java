package com.example.kyc_system.controller;

import com.example.kyc_system.dto.*;
import com.example.kyc_system.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Super Admin-only controller for managing tenants.
 * All endpoints require ROLE_SUPER_ADMIN.
 * Supports CRUD operations, activation/deactivation, API key rotation, and
 * stats retrieval.
 */
@RestController
@RequestMapping("/api/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Super Admin - Tenant Management", description = "Endpoints for superadmin to manage tenants")
public class TenantController {

    private final TenantService tenantService;

    /**
     * Creates a new tenant and optionally provisions a tenant admin user if
     * credentials are provided.
     */
    @PostMapping
    @Operation(summary = "Create new tenant", description = "Creates a new tenant and optionally provisions a tenant admin user")
    public ResponseEntity<TenantDTO> createTenant(@Valid @RequestBody TenantCreateDTO dto) {
        log.info("Creating tenant: tenantId={}", dto.getTenantId());
        TenantDTO created = tenantService.createTenant(dto);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List all tenants", description = "Returns paginated list of all tenants")
    public ResponseEntity<Page<TenantDTO>> getAllTenants(Pageable pageable) {
        return ResponseEntity.ok(tenantService.getAllTenants(pageable));
    }

    @GetMapping("/{tenantId}")
    @Operation(summary = "Get tenant by ID")
    public ResponseEntity<TenantDTO> getTenant(
            @PathVariable String tenantId) {
        return ResponseEntity.ok(tenantService.getTenant(tenantId));
    }

    /**
     * Partially updates tenant configuration (name, email, limits, allowed doc
     * types).
     */
    @PatchMapping("/{tenantId}")
    @Operation(summary = "Update tenant configuration")
    public ResponseEntity<TenantDTO> updateTenant(
            @PathVariable String tenantId,
            @Valid @RequestBody TenantUpdateDTO dto) {
        log.info("Updating tenant: tenantId={}", tenantId);
        return ResponseEntity.ok(tenantService.updateTenant(tenantId, dto));
    }

    /**
     * Deactivates a tenant — blocks all users under this tenant from logging in.
     * Returns whether the state actually changed (vs already inactive).
     */
    @PatchMapping("/{tenantId}/deactivate")
    @Operation(summary = "Deactivate tenant", description = "Blocks all users of this tenant immediately")
    public ResponseEntity<String> deactivate(
            @PathVariable String tenantId) {
        log.info("Deactivating tenant: tenantId={}", tenantId);
        boolean changed = tenantService.setActive(tenantId, false);
        String message = changed ? "Tenant deactivated: " + tenantId : "Tenant is already inactive: " + tenantId;
        return ResponseEntity.ok(message);
    }

    @PatchMapping("/{tenantId}/activate")
    @Operation(summary = "Activate tenant", description = "Re-enables a previously deactivated tenant")
    public ResponseEntity<String> activate(
            @PathVariable String tenantId) {
        log.info("Activating tenant: tenantId={}", tenantId);
        boolean changed = tenantService.setActive(tenantId, true);
        String message = changed ? "Tenant activated: " + tenantId : "Tenant is already active: " + tenantId;
        return ResponseEntity.ok(message);
    }

    /** Generates a new API key for the tenant, invalidating the previous one. */
    @PostMapping("/{tenantId}/rotate-api-key")
    @Operation(summary = "Rotate API key", description = "Generates a new API key, invalidating the old one")
    public ResponseEntity<Map<String, String>> rotateApiKey(
            @PathVariable String tenantId) {
        log.info("Rotating API key for tenant: tenantId={}", tenantId);
        String newKey = tenantService.rotateApiKey(tenantId);
        return ResponseEntity.ok(Map.of("apiKey", newKey));
    }

    /** Returns aggregate KYC statistics for a specific tenant. */
    @GetMapping("/{tenantId}/stats")
    @Operation(summary = "Get tenant stats", description = "Returns KYC stats for a specific tenant")
    public ResponseEntity<TenantStatsDTO> getStats(
            @PathVariable String tenantId) {
        return ResponseEntity.ok(tenantService.getStats(tenantId));
    }
}
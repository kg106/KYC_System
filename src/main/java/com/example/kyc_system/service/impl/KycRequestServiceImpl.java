package com.example.kyc_system.service.impl;

import com.example.kyc_system.client.AuthServiceClient;
import com.example.kyc_system.dto.AuthUserDTO;
import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.KycRequestRepository;

import com.example.kyc_system.service.AuditLogService;
import com.example.kyc_system.service.KycRequestService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;

import com.example.kyc_system.context.TenantContext;
import com.example.kyc_system.dto.KycReportDataDTO;
import com.example.kyc_system.dto.KycRequestSearchDTO;
import com.example.kyc_system.repository.specification.KycRequestSpecification;

import java.util.*;
import java.util.UUID;
import java.time.*;
import org.springframework.security.core.context.*;

/**
 * Implementation of KycRequestService.
 * Manages the KYC request lifecycle, state transition validation, and audit logging.
 * Enforces strict multi-tenant isolation for all operations.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class KycRequestServiceImpl implements KycRequestService {

    private final KycRequestRepository repository;
    private final AuditLogService auditLogService;
    private final AuthServiceClient authServiceClient;

    /** Helper to get current principal name from SecurityContext. */
    private String getCurrentUser() {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        }
        return "SYSTEM";
    }

    /**
     * Creates a new KYC request or re-activates a FAILED one.
     * Starts a NEW transaction to ensure the attempt is recorded even if subsequent
     * steps fail.
     *
     * @param userId       ID of the user submitting the request
     * @param documentType type of document being submitted (PAN/AADHAAR)
     * @return the created or updated KycRequest entity
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KycRequest createOrReuse(String userId, String documentType) {
        String tenantId = TenantContext.getTenant();

        Optional<KycRequest> latestRequest = repository.findTopByUserIdAndDocumentTypeAndTenantIdOrderByCreatedAtDesc(
                UUID.fromString(userId), documentType, UUID.fromString(tenantId));

        if (latestRequest.isPresent()) {
            KycRequest request = latestRequest.get();
            String status = request.getStatus();

            // Prevent duplicate active requests for the same document type
            if (status.equals(KycStatus.PENDING.name()) || status.equals(KycStatus.SUBMITTED.name()) || status.equals(KycStatus.PROCESSING.name())) {
                throw new IllegalStateException("Only one KYC request for " + documentType + " can be processed at a time.");
            }

            // Reuse FAILED request as a new attempt
            if (status.equals(KycStatus.FAILED.name())) {
                request.setAttemptNumber(request.getAttemptNumber() + 1);
                request.setStatus(KycStatus.SUBMITTED.name());
                request.setSubmittedAt(LocalDateTime.now());
                auditLogService.logAction("SUBMIT", "KycRequest", request.getId(), "Re-submitted KYC request for " + documentType,
                        getCurrentUser());
                log.info("KYC request re-submitted: requestId={}, userId={}, docType={}", request.getId(), userId, documentType);
                return request;
            }
        }

        // Create fresh request
        KycRequest newRequest = new KycRequest();
        newRequest.setUserId(UUID.fromString(userId));
        newRequest.setDocumentType(documentType);
        newRequest.setStatus(KycStatus.SUBMITTED.name());
        newRequest.setAttemptNumber(1);
        newRequest.setSubmittedAt(LocalDateTime.now());
        newRequest.setTenantId(UUID.fromString(tenantId));

        try {
            KycRequest saved = repository.save(newRequest);
            auditLogService.logAction("SUBMIT", "KycRequest", saved.getId(), "Submitted new KYC request for " + documentType,
                    getCurrentUser());
            log.info("New KYC request created: requestId={}, userId={}, docType={}, tenantId={}", saved.getId(), userId, documentType, tenantId);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("Only one KYC request for " + documentType + " can be processed at a time.");
        }
    }

    /**
     * Updates request status (e.g., to VERIFIED or FAILED) and logs the event.
     *
     * @param requestId ID of the request to update
     * @param status    the new status to apply
     */
    @Override
    @Transactional
    public void updateStatus(Long requestId, KycStatus status) {
        int updatedRows = repository.updateStatus(requestId, status.name());

        if (updatedRows == 0) {
            throw new RuntimeException("KYC request not found with id: " + requestId);
        }

        auditLogService.logAction("UPDATE_STATUS", "KycRequest", requestId, "Updated status to " + status, getCurrentUser());
        log.info("KYC status updated: requestId={}, newStatus={}", requestId, status);
    }

    /**
     * Fetches the latest request for a user with EAGER loading of document data.
     * Logs the view action.
     *
     * @param userId user ID
     * @return Optional containing the latest request if found
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<KycRequest> getLatestByUser(String userId) {
        Optional<KycRequest> request = repository.findTopByUserIdOrderByCreatedAtDesc(UUID.fromString(userId));
        request.ifPresent(r -> {
            r.getKycDocuments().forEach(doc -> doc.getExtractedData().size());
        });

        if (request.isPresent()) {
            auditLogService.logAction("VIEW_STATUS", "KycRequest", request.get().getId(),
                    "Viewed latest KYC status", getCurrentUser());
        }

        return request;
    }

    /**
     * Fetches all requests for a user, including history.
     *
     * @param userId user ID
     * @return list of all KYC requests for the user
     */
    @Override
    @Transactional(readOnly = true)
    public List<KycRequest> getAllByUser(String userId) {
        List<KycRequest> requests = repository.findByUserId(UUID.fromString(userId));
        requests.forEach(r -> {
            r.getKycDocuments().forEach(doc -> doc.getExtractedData().size());
        });

        auditLogService.logAction("VIEW_HISTORY", "User", null, "Viewed KYC history for " + userId, getCurrentUser());

        return requests;
    }

    /**
     * Searches requests with tenant-aware filtering and pagination.
     * Supports super-admin global search.
     *
     * @param searchDTO search criteria (user ID, name, status, etc.)
     * @param pageable  pagination info
     * @return page of KYC requests matching the criteria
     */
    @Override
    @Transactional(readOnly = true)
    public Page<KycRequest> searchKycRequests(KycRequestSearchDTO searchDTO, Pageable pageable) {
        String tenantId = TenantContext.getTenant();
        boolean isSuperAdmin = TenantContext.isSuperAdmin();

        Page<KycRequest> requests = repository.findAll(
                KycRequestSpecification.buildSpecification(searchDTO, tenantId, isSuperAdmin), pageable);
        requests.forEach(r -> {
            r.getKycDocuments().forEach(doc -> doc.getExtractedData().size());
        });

        Map<String, Object> searchCriteria = new HashMap<>();
        if (searchDTO.getUserId() != null)
            searchCriteria.put("userId", searchDTO.getUserId());
        if (searchDTO.getUserName() != null)
            searchCriteria.put("userName", searchDTO.getUserName());
        if (searchDTO.getStatus() != null)
            searchCriteria.put("status", searchDTO.getStatus());
        if (searchDTO.getDocumentType() != null)
            searchCriteria.put("documentType", searchDTO.getDocumentType());

        auditLogService.logAction("SEARCH_KYC", "KycRequest", null, searchCriteria, getCurrentUser());

        return requests;
    }

    /**
     * Generates report data by aggregating requests across specified date range.
     * Fetches user details from Auth Service for each unique user to populate
     * name, email, mobile, and DOB fields.
     *
     * @param dateFrom start date
     * @param dateTo   end date
     * @param tenantId optional tenant filter
     * @return list of report DTOs
     */
    @Override
    @Transactional
    public List<KycReportDataDTO> getReportData(LocalDate dateFrom, LocalDate dateTo, String tenantId) {
        List<KycReportDataDTO> report = new ArrayList<>();

        List<KycRequest> result;
        if (tenantId != null) {
            result = repository.findByCreatedAtBetweenWithUserAndTenant(UUID.fromString(tenantId), dateFrom.atStartOfDay(),
                    dateTo.atTime(LocalTime.MAX));
        } else {
            result = repository.findByCreatedAtBetweenWithUserAndTenant(dateFrom.atStartOfDay(),
                    dateTo.atTime(LocalTime.MAX));
        }

        // Cache user lookups to avoid redundant HTTP calls for the same user
        Map<String, AuthUserDTO> userCache = new HashMap<>();

        for (KycRequest kr : result) {
            String userId = kr.getUserId().toString();

            // Fetch from Auth Service (cached per userId)
            AuthUserDTO user = userCache.computeIfAbsent(userId, id -> {
                try {
                    return authServiceClient.getUserById(id);
                } catch (Exception e) {
                    log.warn("Failed to fetch user {} from Auth Service for report: {}", id, e.getMessage());
                    return null;
                }
            });

            KycReportDataDTO temp = KycReportDataDTO.builder()
                    .userId(kr.getUserId().toString())
                    .kycRequestId(kr.getId())
                    .userName(user != null ? user.getName() : "Unknown")
                    .email(user != null ? user.getEmail() : null)
                    .mobileNumber(user != null ? user.getMobileNumber() : null)
                    .dob(user != null ? user.getDob() : null)
                    .status(kr.getStatus())
                    .tenantId(kr.getTenantId().toString())
                    .tenantName(kr.getTenantId().toString()) // tenant UUID — could be enriched later with tenant name endpoint
                    .documentType(kr.getDocumentType())
                    .attemptNumber(kr.getAttemptNumber())
                    .nameMatchScore(null)
                    .decisionReason(kr.getFailureReason())
                    .build();
            report.add(temp);
        }

        return report;
    }
}

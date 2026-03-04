package com.example.kyc_system.service;

import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.entity.Tenant;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.exception.BusinessException;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.repository.TenantRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;

import com.example.kyc_system.context.TenantContext;
import com.example.kyc_system.dto.KycRequestSearchDTO;
import com.example.kyc_system.repository.specification.KycRequestSpecification;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class KycRequestServiceImpl implements KycRequestService {

    private final KycRequestRepository repository;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final TenantRepository tenantRepository; // ← add this

    private String getCurrentUser() {
        if (org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null) {
            return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()
                    .getName();
        }
        return "SYSTEM";
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KycRequest createOrReuse(Long userId, String documentType) {
        String tenantId = TenantContext.getTenant();
        LocalDateTime startOfDay = LocalDateTime.now()
                .with(java.time.LocalTime.MIN);

        // Use tenant-specific daily limit
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        long totalAttemptsToday = repository
                .sumAttemptNumberByUserIdAndTenantIdAndSubmittedAtAfter(
                        userId, tenantId, startOfDay);

        if (totalAttemptsToday >= tenant.getMaxDailyAttempts()) {
            throw new RuntimeException(
                    "Daily KYC attempt limit reached ("
                            + tenant.getMaxDailyAttempts()
                            + "). Please try again tomorrow.");
        }

        Optional<KycRequest> latestRequest = repository
                .findTopByUserIdAndDocumentTypeAndTenantIdOrderByCreatedAtDesc(
                        userId, documentType, tenantId);

        if (latestRequest.isPresent()) {
            KycRequest request = latestRequest.get();
            String status = request.getStatus();

            if (status.equals(KycStatus.PENDING.name()) ||
                    status.equals(KycStatus.SUBMITTED.name()) ||
                    status.equals(KycStatus.PROCESSING.name())) {
                throw new RuntimeException(
                        "Only one KYC request for " + documentType
                                + " can be processed at a time.");
            }

            if (status.equals(KycStatus.FAILED.name())) {
                request.setAttemptNumber(request.getAttemptNumber() + 1);
                request.setStatus(KycStatus.SUBMITTED.name());
                request.setSubmittedAt(LocalDateTime.now());
                auditLogService.logAction("SUBMIT", "KycRequest",
                        request.getId(),
                        "Re-submitted KYC request for " + documentType,
                        getCurrentUser());
                return request;
            }
        }

        User user = userService.getActiveUser(userId);
        KycRequest newRequest = new KycRequest();
        newRequest.setUser(user);
        newRequest.setDocumentType(documentType);
        newRequest.setStatus(KycStatus.SUBMITTED.name());
        newRequest.setAttemptNumber(1);
        newRequest.setSubmittedAt(LocalDateTime.now());
        newRequest.setTenantId(tenantId); // ← scope to tenant

        try {
            KycRequest saved = repository.save(newRequest);
            auditLogService.logAction("SUBMIT", "KycRequest",
                    saved.getId(),
                    "Submitted new KYC request for " + documentType,
                    getCurrentUser());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(
                    "Only one request can be processed at a time");
        }
    }

    @Override
    public void updateStatus(Long requestId, KycStatus status) {
        KycRequest request = repository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("KYC request not found"));
        request.setStatus(String.valueOf(status));

        auditLogService.logAction("UPDATE_STATUS", "KycRequest", requestId,
                "Updated status to " + status, getCurrentUser());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KycRequest> getLatestByUser(Long userId) {
        Optional<KycRequest> request = repository.findTopByUserIdOrderByCreatedAtDesc(userId);
        request.ifPresent(r -> {
            r.getKycDocuments().forEach(doc -> doc.getExtractedData().size());
        });

        if (request.isPresent()) {
            auditLogService.logAction("VIEW_STATUS", "KycRequest", request.get().getId(),
                    "Viewed latest KYC status", getCurrentUser());
        }

        return request;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<KycRequest> getAllByUser(Long userId) {
        java.util.List<KycRequest> requests = repository.findByUserId(userId);
        requests.forEach(r -> {
            r.getKycDocuments().forEach(doc -> doc.getExtractedData().size());
        });

        auditLogService.logAction("VIEW_HISTORY", "User", userId,
                "Viewed KYC history", getCurrentUser());

        return requests;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<KycRequest> searchKycRequests(KycRequestSearchDTO searchDTO, Pageable pageable) {
        Page<KycRequest> requests = repository.findAll(KycRequestSpecification.buildSpecification(searchDTO), pageable);
        requests.forEach(r -> {
            r.getKycDocuments().forEach(doc -> doc.getExtractedData().size());
        });

        java.util.Map<String, Object> searchCriteria = new java.util.HashMap<>();
        if (searchDTO.getUserId() != null)
            searchCriteria.put("userId", searchDTO.getUserId());
        if (searchDTO.getUserName() != null)
            searchCriteria.put("userName", searchDTO.getUserName());
        if (searchDTO.getStatus() != null)
            searchCriteria.put("status", searchDTO.getStatus());
        if (searchDTO.getDocumentType() != null)
            searchCriteria.put("documentType", searchDTO.getDocumentType());

        auditLogService.logAction("SEARCH_KYC", "KycRequest", null,
                searchCriteria, getCurrentUser());

        return requests;
    }
}

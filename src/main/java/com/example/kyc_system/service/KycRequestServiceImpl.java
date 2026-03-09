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
import com.example.kyc_system.dto.KycReportDataDTO;
import com.example.kyc_system.dto.KycRequestSearchDTO;
import com.example.kyc_system.repository.specification.KycRequestSpecification;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.*;
import java.time.*;
import org.springframework.security.core.context.*;

@Service
@Transactional
@RequiredArgsConstructor
public class KycRequestServiceImpl implements KycRequestService {

    private final KycRequestRepository repository;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final TenantRepository tenantRepository; // ← add this

    private String getCurrentUser() {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        }
        return "SYSTEM";
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KycRequest createOrReuse(Long userId, String documentType) {
        String tenantId = TenantContext.getTenant();
        LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);

        // Use tenant-specific daily limit
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        long totalAttemptsToday = repository.sumAttemptNumberByUserIdAndTenantIdAndSubmittedAtAfter(
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
        // Superadmin bypass: they can see latest regardless of tenant if they are
        // looking at a specific user
        // But usually, getLatestByUser is called in context of a user.
        // If TenantContext.getTenant() is SUPER_ADMIN,
        // findTopByUserIdOrderByCreatedAtDesc
        // works fine as it's not scoped by tenantId in the repository method itself.
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
    public List<KycRequest> getAllByUser(Long userId) {
        // findByUserId is unscoped in KycRequestRepository
        List<KycRequest> requests = repository.findByUserId(userId);
        requests.forEach(r -> {
            r.getKycDocuments().forEach(doc -> doc.getExtractedData().size());
        });

        auditLogService.logAction("VIEW_HISTORY", "User", userId, "Viewed KYC history", getCurrentUser());

        return requests;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<KycRequest> searchKycRequests(KycRequestSearchDTO searchDTO, Pageable pageable) {
        // Pass tenant info to specification
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

    @Override
    @Transactional
    public List<KycReportDataDTO> getReportData(LocalDate dateFrom, LocalDate dateTo) {
        List<KycReportDataDTO> report = new ArrayList<>();

        List<KycRequest> result = repository.findByCreatedAtBetweenWithUserAndTenant(dateFrom.atStartOfDay(),
                dateTo.atTime(LocalTime.MAX));

        for (KycRequest kr : result) {
            KycReportDataDTO temp = KycReportDataDTO.builder()
                    .userId(kr.getUser().getId())
                    .kycRequestId(kr.getId())
                    .userName(kr.getUser().getName())
                    .dob(kr.getUser().getDob())
                    .status(kr.getStatus())
                    .mobileNumber(kr.getUser().getMobileNumber())
                    .email(kr.getUser().getEmail())
                    .tenantId(kr.getTenantId())
                    .tenantName(kr.getUser().getTenant().getName())
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

package com.example.kyc_system.service;

import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.KycRequestRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    private String getCurrentUser() {
        if (org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null) {
            return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()
                    .getName();
        }
        return "SYSTEM";
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public KycRequest createOrReuse(Long userId, String documentType) {
        LocalDateTime startOfDay = LocalDateTime.now().with(java.time.LocalTime.MIN);

        // Sum attemptNumber across all requests for the user today
        long totalAttemptsToday = repository.sumAttemptNumberByUserIdAndSubmittedAtGreaterThanEqual(userId, startOfDay);

        if (totalAttemptsToday >= 5) {
            throw new RuntimeException("Daily KYC attempt limit reached (5). Please try again tomorrow.");
        }

        // Only reuse failed requests for the SAME document type
        Optional<KycRequest> latestRequest = repository.findTopByUserIdAndDocumentTypeOrderByCreatedAtDesc(userId,
                documentType);

        if (latestRequest.isPresent()) {
            KycRequest request = latestRequest.get();
            String status = request.getStatus();

            if (status.equals(KycStatus.PENDING.name()) ||
                    status.equals(KycStatus.SUBMITTED.name()) ||
                    status.equals(KycStatus.PROCESSING.name())) {
                throw new RuntimeException(
                        "Only one KYC request for " + documentType + " can be processed at a time. Please wait.");
            }

            if (status.equals(KycStatus.FAILED.name())) {
                request.setAttemptNumber(request.getAttemptNumber() + 1);
                request.setStatus(KycStatus.SUBMITTED.name());
                request.setSubmittedAt(LocalDateTime.now());

                auditLogService.logAction("SUBMIT", "KycRequest", request.getId(),
                        "Re-submitted KYC request for " + documentType, getCurrentUser());

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
        try {
            KycRequest savedRequest = repository.save(newRequest);
            auditLogService.logAction("SUBMIT", "KycRequest", savedRequest.getId(),
                    "Submitted new KYC request for " + documentType, getCurrentUser());
            return savedRequest;
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new com.example.kyc_system.exception.BusinessException("Only one request can be processed at a time");
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

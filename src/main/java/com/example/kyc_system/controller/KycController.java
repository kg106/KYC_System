package com.example.kyc_system.controller;

import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.scheduler.KycReportScheduler;
import com.example.kyc_system.service.impl.KycOrchestrationService;
import com.example.kyc_system.service.KycRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.kyc_system.dto.KycRequestSearchDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import io.swagger.v3.oas.annotations.*;
import org.springdoc.core.annotations.*;
import com.example.kyc_system.context.*;
import com.example.kyc_system.entity.*;
import com.example.kyc_system.util.*;
import java.util.*;
import java.util.stream.*;
import org.springframework.http.*;
import io.swagger.v3.oas.annotations.tags.*;

/**
 * KYC Operations Controller.
 * Provides endpoints for document submission, status tracking, search, and manual report generation.
 * Secured with method-level (@PreAuthorize) access control.
 */
@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "KYC Operations", description = "Endpoints for KYC document upload and status verification")
public class KycController {

    private final KycOrchestrationService orchestrationService;
    private final KycRequestService requestService;
    private final KycReportScheduler reportScheduler;

    /**
     * Uploads a KYC document and initiates the verification process.
     * Consumes multipart form data.
     *
     * @param userId ID of the user submitting the document
     * @param documentType type of document (e.g., PAN, AADHAAR)
     * @param file the actual multipart file
     * @param documentNumber the identification number on the document
     * @return a response indicating acceptance and the generated Request ID
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@securityService.canAccessUser(#userId)")
    @Operation(summary = "Upload KYC Document", description = "Upload a KYC document for verification")
    public ResponseEntity<?> uploadDocument(
            @Parameter(description = "User ID", required = true) @RequestParam("userId") String userId,
            @Parameter(description = "Document Type", required = true) @RequestParam("documentType") DocumentType documentType,
            @Parameter(description = "KYC Document File", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "Document Number", required = true) @RequestParam("documentNumber") String documentNumber) {
        try {
            log.info("KYC upload: userId={}, docType={}, fileName={}", userId, documentType, file.getOriginalFilename());
            Long requestId = orchestrationService.submitKyc(userId, documentType, file, documentNumber);
            log.info("KYC upload successful: userId={}, requestId={}", userId, requestId);
            return ResponseEntity.accepted().body(Map.of("message", "KYC request submitted successfully", "requestId", requestId));
        } catch (Exception e) {
            log.warn("KYC upload failed: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Retrieves the most recent KYC status for a user.
     *
     * @param userId user ID
     * @return latest KYC status summary
     */
    @GetMapping("/status/{userId}")
    @PreAuthorize("@securityService.canAccessUser(#userId)")
    @Operation(summary = "Get KYC Status", description = "Retrieves the latest KYC request status for a specific user")
    public ResponseEntity<?> getKycStatus(@PathVariable String userId) {
        log.debug("KYC status lookup: userId={}", userId);
        return requestService.getLatestByUser(userId).map(request -> ResponseEntity.ok(formatKycResponse(request)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No KYC request found for this user")));
    }

    /**
     * Retrieves all KYC request attempts for a user.
     *
     * @param userId user ID
     * @return list of all KYC attempts
     */
    @GetMapping("/status/all/{userId}")
    @PreAuthorize("@securityService.canAccessUser(#userId)")
    @Operation(summary = "Get All KYC Requests Status", description = "Retrieves all KYC request history for a specific user. Available to self or ADMIN.")
    public ResponseEntity<?> getAllKycStatus(@PathVariable String userId) {
        List<KycRequest> requests = requestService.getAllByUser(userId);

        if (requests.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No KYC history found for this user"));
        }

        List<Map<String, Object>> response = requests.stream().map(this::formatKycResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Searches for KYC requests based on various filters.
     * Restricted to ADMIN role.
     *
     * @param userId filter by user ID (optional)
     * @param userName filter by name (optional)
     * @param status filter by status (optional)
     * @param documentType filter by document type (optional)
     * @param dateFrom filter by submission date (optional)
     * @param dateTo filter by submission date (optional)
     * @param pageable pagination parameters
     * @return paged list of matching requests
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search KYC Requests", description = "Search KYC requests with filters and pagination. Restricted to ADMIN.")
    public ResponseEntity<Page<Map<String, Object>>> searchKycRequests(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) LocalDateTime dateFrom,
            @RequestParam(required = false) LocalDateTime dateTo,
            @ParameterObject Pageable pageable) {

        KycRequestSearchDTO searchDTO = KycRequestSearchDTO.builder()
                .userId(userId)
                .userName(userName)
                .status(status)
                .documentType(documentType)
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .build();

        log.info("KYC search: userId={}, status={}, docType={}", userId, status, documentType);
        Page<KycRequest> requests = requestService.searchKycRequests(searchDTO, pageable);
        Page<Map<String, Object>> response = requests.map(this::formatKycResponse);
        return ResponseEntity.ok(response);
    }

    /**
     * Transforms a KycRequest entity into a simplified JSON map for the UI.
     * Includes sensitive data masking for administrators.
     *
     * @param request the request entity
     * @return a map of UI-friendly fields
     */
    private Map<String, Object> formatKycResponse(KycRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", request.getId());
        response.put("status", request.getStatus());
        response.put("failureReason", request.getFailureReason() != null ? request.getFailureReason() : "");
        response.put("attemptNumber", request.getAttemptNumber());
        response.put("submittedAt", request.getCreatedAt());

        // Get extracted data from documents
        if (request.getKycDocuments() != null && !request.getKycDocuments().isEmpty()) {
            KycDocument doc = request.getKycDocuments().iterator().next();
            response.put("documentType", doc.getDocumentType());

            if (doc.getExtractedData() != null && !doc.getExtractedData().isEmpty()) {
                KycExtractedData data = doc.getExtractedData().iterator().next();
                response.put("extractedName", data.getExtractedName());
                response.put("extractedDob", data.getExtractedDob());

                String docNumber = data.getExtractedDocumentNumber();
                // Mask if Admin
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") ||
                        a.getAuthority().equals("ROLE_TENANT_ADMIN") ||
                        a.getAuthority().equals("ROLE_SUPER_ADMIN"));
                if (isAdmin) {
                    docNumber = MaskingUtil.maskDocumentNumber(docNumber);
                }
                response.put("extractedDocumentNumber", docNumber);
            }
        }
        return response;
    }

    /**
     * Triggers a manual KYC report generation and email dispatch.
     * Restricted to ADMIN role.
     *
     * @param dateFrom custom start date (optional)
     * @param dateTo custom end date (optional)
     * @return confirmation message
     */
    @PostMapping("/report")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Generate KYC Report", description = "Manually generates and emails the KYC report. By default, it sends the report for the previous month. You can also specify custom date range and a recipient email.")
    public ResponseEntity<String> triggerReport(
            @Parameter(description = "Start date in YYYY-MM-DD format (optional)") @RequestParam(required = false) String dateFrom,
            @Parameter(description = "End date in YYYY-MM-DD format (optional)") @RequestParam(required = false) String dateTo,
            @Parameter(description = "Recipient email address (optional)") @RequestParam(required = false) String email) {

        LocalDate df;
        LocalDate dt;

        if (dateFrom != null && !dateFrom.isBlank() && dateTo != null && !dateTo.isBlank()) {
            df = LocalDate.parse(dateFrom);
            dt = LocalDate.parse(dateTo);
        } else {
            // Default to last month
            YearMonth lastMonth = YearMonth.now().minusMonths(1);
            df = lastMonth.atDay(1);
            dt = lastMonth.atEndOfMonth();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isSuperAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        String tenantId = null;

        if (!isSuperAdmin) {
            // For Tenant Admin or regular Admin, restrict to their own tenant
            tenantId = TenantContext.getTenant();
            log.info("KYC report restricted to tenant: {}", tenantId);
        } else {
            log.info("KYC report requested for all tenants (Super Admin)");
        }

        log.info("KYC report triggered: from={}, to={}, email={}, tenant={}", df, dt, email,
                tenantId != null ? tenantId : "ALL");
        reportScheduler.triggerManually(df, dt, tenantId, email);

        String message = String.format("Report triggered for range: %s to %s. Scope: %s. Delivery: %s",
                df, dt, (tenantId != null ? "Tenant " + tenantId : "All Tenants"),
                (email != null ? email : "Default Recipients"));

        return ResponseEntity.ok(message);
    }
}

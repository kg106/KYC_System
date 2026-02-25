package com.example.kyc_system.controller;

import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.service.KycOrchestrationService;
import com.example.kyc_system.service.KycRequestService;
//import com.example.kyc_system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.kyc_system.dto.KycRequestSearchDTO;
import java.util.Map;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "KYC Operations", description = "Endpoints for KYC document upload and status verification")
public class KycController {

    private final KycOrchestrationService orchestrationService;
    private final KycRequestService requestService;
    // private final UserService userService;

    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@securityService.canAccessUser(#userId)")
    @io.swagger.v3.oas.annotations.Operation(summary = "Upload KYC Document", description = "Upload a KYC document for verification")
    public ResponseEntity<?> uploadDocument(
            @io.swagger.v3.oas.annotations.Parameter(description = "User ID", required = true) @RequestParam("userId") Long userId,
            @io.swagger.v3.oas.annotations.Parameter(description = "Document Type", required = true) @RequestParam("documentType") DocumentType documentType,
            @io.swagger.v3.oas.annotations.Parameter(description = "KYC Document File", required = true) @RequestParam("file") MultipartFile file,
            @io.swagger.v3.oas.annotations.Parameter(description = "Document Number", required = true) @RequestParam("documentNumber") String documentNumber) {
        try {
            Long requestId = orchestrationService.submitKyc(userId, documentType, file, documentNumber);
            return ResponseEntity.accepted().body(Map.of(
                    "message", "KYC request submitted successfully",
                    "requestId", requestId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status/{userId}")
    @PreAuthorize("@securityService.canAccessUser(#userId)")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get KYC Status", description = "Retrieves the latest KYC request status for a specific user")
    public ResponseEntity<?> getKycStatus(@PathVariable Long userId) {
        return requestService.getLatestByUser(userId)
                .map(request -> ResponseEntity.ok(formatKycResponse(request)))
                .orElse(ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body(java.util.Map.of("message", "No KYC request found for this user")));
    }

    @GetMapping("/status/all/{userId}")
    @PreAuthorize("@securityService.canAccessUser(#userId)")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get All KYC Requests Status", description = "Retrieves all KYC request history for a specific user. Available to self or ADMIN.")
    public ResponseEntity<?> getAllKycStatus(@PathVariable Long userId) {
        java.util.List<com.example.kyc_system.entity.KycRequest> requests = requestService.getAllByUser(userId);

        if (requests.isEmpty()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                    .body(java.util.Map.of("message", "No KYC history found for this user"));
        }

        java.util.List<java.util.Map<String, Object>> response = requests.stream()
                .map(this::formatKycResponse)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    @io.swagger.v3.oas.annotations.Operation(summary = "Search KYC Requests", description = "Search KYC requests with filters and pagination. Restricted to ADMIN.")
    public ResponseEntity<Page<Map<String, Object>>> searchKycRequests(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) LocalDateTime dateFrom,
            @RequestParam(required = false) LocalDateTime dateTo,
            @org.springdoc.core.annotations.ParameterObject Pageable pageable) {

        KycRequestSearchDTO searchDTO = KycRequestSearchDTO.builder()
                .userId(userId)
                .userName(userName)
                .status(status)
                .documentType(documentType)
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .build();

        Page<com.example.kyc_system.entity.KycRequest> requests = requestService.searchKycRequests(searchDTO, pageable);
        Page<Map<String, Object>> response = requests.map(this::formatKycResponse);
        return ResponseEntity.ok(response);
    }

    private java.util.Map<String, Object> formatKycResponse(com.example.kyc_system.entity.KycRequest request) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("requestId", request.getId());
        response.put("status", request.getStatus());
        response.put("failureReason", request.getFailureReason() != null ? request.getFailureReason() : "");
        response.put("attemptNumber", request.getAttemptNumber());
        response.put("submittedAt", request.getCreatedAt());

        // Get extracted data from documents
        if (request.getKycDocuments() != null && !request.getKycDocuments().isEmpty()) {
            com.example.kyc_system.entity.KycDocument doc = request.getKycDocuments().iterator().next();
            response.put("documentType", doc.getDocumentType());

            if (doc.getExtractedData() != null && !doc.getExtractedData().isEmpty()) {
                com.example.kyc_system.entity.KycExtractedData data = doc.getExtractedData().iterator().next();
                response.put("extractedName", data.getExtractedName());
                response.put("extractedDob", data.getExtractedDob());

                String docNumber = data.getExtractedDocumentNumber();
                // Mask if Admin
                if (org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()
                        .getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                    docNumber = com.example.kyc_system.util.MaskingUtil.maskDocumentNumber(docNumber);
                }
                response.put("extractedDocumentNumber", docNumber);
            }
        }
        return response;
    }
}

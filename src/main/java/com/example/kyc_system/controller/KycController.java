package com.example.kyc_system.controller;

import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.service.KycOrchestrationService;
import com.example.kyc_system.service.KycRequestService;
import com.example.kyc_system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "KYC Operations", description = "Endpoints for KYC document upload and status verification")
public class KycController {

    private final KycOrchestrationService orchestrationService;
    private final KycRequestService requestService;
    private final UserService userService;

    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@securityService.canAccessUser(#userId)")
    @io.swagger.v3.oas.annotations.Operation(summary = "Upload KYC Document", description = "Upload a KYC document for verification")
    public ResponseEntity<?> uploadDocument(
            @io.swagger.v3.oas.annotations.Parameter(description = "User ID", required = true) @RequestParam("userId") Long userId,
            @io.swagger.v3.oas.annotations.Parameter(description = "Document Type", required = true) @RequestParam("documentType") DocumentType documentType,
            @io.swagger.v3.oas.annotations.Parameter(description = "KYC Document File", required = true) @RequestParam("file") MultipartFile file,
            @io.swagger.v3.oas.annotations.Parameter(description = "Document Number", required = true) @RequestParam("documentNumber") String documentNumber) {
        try {
            orchestrationService.processKyc(userId, documentType, file, documentNumber);
            return ResponseEntity.ok(Map.of("message", "KYC processing started successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status/{userId}")
    @PreAuthorize("@securityService.canAccessUser(#userId)")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get KYC Status", description = "Retrieves the latest KYC request status for a specific user")
    public ResponseEntity<?> getKycStatus(@PathVariable Long userId) {
        return requestService.getLatestByUser(userId)
                .map(request -> ResponseEntity.ok(Map.of(
                        "requestId", request.getId(),
                        "status", request.getStatus(),
                        "failureReason", request.getFailureReason(),
                        "attemptNumber", request.getAttemptNumber(),
                        "submittedAt", request.getCreatedAt())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/all/{userId}")
    @PreAuthorize("@securityService.canAccessUser(#userId)")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get All KYC Requests Status", description = "Retrieves all KYC request history for a specific user. Available to self or ADMIN.")
    public ResponseEntity<java.util.List<?>> getAllKycStatus(@PathVariable Long userId) {
        java.util.List<com.example.kyc_system.entity.KycRequest> requests = requestService.getAllByUser(userId);
        java.util.List<java.util.Map<String, Object>> response = requests.stream()
                .map(request -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("requestId", request.getId());
                    map.put("status", request.getStatus());
                    map.put("failureReason", request.getFailureReason() != null ? request.getFailureReason() : "");
                    map.put("attemptNumber", request.getAttemptNumber());
                    map.put("submittedAt", request.getCreatedAt());
                    return map;
                })
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(response);
    }
}

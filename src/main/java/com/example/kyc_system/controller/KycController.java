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
public class KycController {

    private final KycOrchestrationService orchestrationService;
    private final KycRequestService requestService;
    private final UserService userService;

    @PostMapping("/upload")
    @PreAuthorize("@securityService.canAccessUser(#userId)")
    public ResponseEntity<?> uploadDocument(@RequestParam("userId") Long userId,
            @RequestParam("documentType") DocumentType documentType,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentNumber") String documentNumber) {
        try {
            orchestrationService.processKyc(userId, documentType, file, documentNumber);
            return ResponseEntity.ok(Map.of("message", "KYC processing started successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status/{userId}")
    @PreAuthorize("@securityService.canAccessUser(#userId)")
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
}

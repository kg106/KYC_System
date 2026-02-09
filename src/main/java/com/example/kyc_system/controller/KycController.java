package com.example.kyc_system.controller;

import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.service.KycOrchestrationService;
import com.example.kyc_system.service.KycRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycOrchestrationService orchestrationService;
    private final KycRequestService requestService;

    @PostMapping("/upload")
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

package com.example.kyc_system.queue;

import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.KycRequestRepository;

import com.example.kyc_system.service.impl.KycOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Recovers KYC requests that were lost from the in-memory queue due to a
 * JVM crash or system restart.
 *
 * On application startup, queries the database for requests stuck in
 * SUBMITTED or PROCESSING status and re-queues them for the worker to pick up.
 *
 * - SUBMITTED requests are re-queued as-is (the CAS in processAsync will
 * transition them to PROCESSING).
 * - PROCESSING requests are reset back to SUBMITTED first (the crash
 * interrupted mid-OCR, so the CAS needs to succeed again).
 *
 * This is idempotent: even if a request is already in the queue, the CAS
 * in
 * {@link KycOrchestrationService#processAsync}
 * guarantees only one execution proceeds.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KycQueueRecoveryService {

    private final KycRequestRepository repository;
    private final KycQueueService queueService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverStuckRequests() {
        // 1. Re-queue SUBMITTED requests (no status change needed)
        List<KycRequest> submitted = repository.findByStatus(KycStatus.SUBMITTED.name());
        for (KycRequest req : submitted) {
            queueService.push(req.getId());
            log.info("Recovered SUBMITTED request id={} back into queue", req.getId());
        }

        // 2. Reset PROCESSING → SUBMITTED and re-queue
        // These were mid-OCR when the crash happened; CAS needs SUBMITTED to proceed.
        List<KycRequest> processing = repository.findByStatus(KycStatus.PROCESSING.name());
        for (KycRequest req : processing) {
            req.setStatus(KycStatus.SUBMITTED.name());
            queueService.push(req.getId());
            log.info("Reset PROCESSING→SUBMITTED and recovered request id={}", req.getId());
        }

        int total = submitted.size() + processing.size();
        if (total > 0) {
            log.info("Queue recovery complete: re-queued {} request(s)", total);
        } else {
            log.info("Queue recovery: no stuck requests found");
        }
    }
}

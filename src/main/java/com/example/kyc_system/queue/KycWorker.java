package com.example.kyc_system.queue;

import com.example.kyc_system.service.KycOrchestrationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Background worker that continuously polls the KYC queue for pending requests
 * and triggers asynchronous OCR + verification processing.
 *
 * Runs on a dedicated daemon thread — starts automatically when the application
 * boots.
 * If processing fails, the error is caught and logged (won't crash the worker
 * loop).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KycWorker {

    private final KycQueueService queueService;
    private final KycOrchestrationService orchestrationService;
    private volatile boolean running = true;

    /**
     * Spawns a daemon thread that continuously polls requests from the queue.
     * Daemon thread = automatically stopped when the JVM shuts down.
     */
    @PostConstruct
    public void start() {
        Thread worker = new Thread(() -> {
            while (running) {
                try {
                    // Blocks until a request ID becomes available in the queue
                    Long requestId = queueService.poll();
                    log.info("Processing KYC request from queue: {}", requestId);
                    // Delegates to orchestration service for OCR → extract → verify pipeline
                    orchestrationService.processAsync(requestId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("KYC worker interrupted, shutting down.");
                    break;
                } catch (Throwable t) {
                    log.error("Fatal error in KYC worker while processing request", t);
                    // Don't break — keep the worker alive for the next request
                }
            }
        });
        worker.setName("kyc-worker");
        worker.setDaemon(true); // Won't prevent JVM shutdown
        worker.start();
    }

    @PreDestroy // ← add this method
    public void stop() {
        running = false;
    }

}

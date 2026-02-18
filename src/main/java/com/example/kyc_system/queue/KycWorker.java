package com.example.kyc_system.queue;

import com.example.kyc_system.service.KycOrchestrationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
public class KycWorker {

    private final KycQueueService queueService;
    private final KycOrchestrationService orchestrationService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10); // 10 Concurrent Workers

    @PostConstruct
    public void startWorkers() {
        for (int i = 0; i < 10; i++) {
            executorService.submit(this::processQueue);
        }
    }

    private void processQueue() {
        while (true) {
            try {
                Long requestId = queueService.poll();
                orchestrationService.processAsync(requestId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing KYC request", e);
            }
        }
    }
}

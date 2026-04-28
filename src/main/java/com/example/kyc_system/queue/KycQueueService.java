package com.example.kyc_system.queue;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory queue for KYC request processing.
 * Decouples the document upload (synchronous) from the heavy OCR processing
 * (asynchronous).
 * KycWorker threads poll from this queue to process requests.
 */
@Service
@Slf4j
public class KycQueueService {

    /**
     * Unbounded, thread-safe blocking queue — stores KYC request IDs waiting to be
     * processed.
     */
    private final BlockingQueue<Long> queue = new LinkedBlockingQueue<>();

    /** Adds a KYC request ID to the queue (non-blocking). */
    /**
     * Pushes a KYC request ID onto the processing queue.
     * This is a non-blocking operation (uses offer()).
     *
     * @param requestId the ID of the request to process
     */
    public void push(Long requestId) {
        log.info("Pushing request to queue: requestId={}", requestId);
        queue.offer(requestId);
    }

    /**
     * Blocks until a request ID is available, then returns it. Used by worker
     * threads.
     */
    public Long poll() throws InterruptedException {
        Long requestId = queue.take();
        log.info("Polled request from queue: requestId={}, remainingSize={}", requestId, queue.size());
        return requestId;
    }

    public int size() {
        return queue.size();
    }
}

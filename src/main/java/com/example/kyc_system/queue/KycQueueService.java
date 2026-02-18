package com.example.kyc_system.queue;

import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class KycQueueService {

    private final BlockingQueue<Long> queue = new LinkedBlockingQueue<>();

    public void push(Long requestId) {
        queue.offer(requestId);
    }

    public Long poll() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }
}

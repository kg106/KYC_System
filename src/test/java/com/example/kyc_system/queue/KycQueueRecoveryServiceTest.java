package com.example.kyc_system.queue;

import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.KycRequestRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KycQueueRecoveryService}.
 *
 * Covers:
 * - No stuck requests → nothing queued
 * - Only SUBMITTED requests → re-queued without status change
 * - Only PROCESSING requests → reset to SUBMITTED + re-queued
 * - Mixed SUBMITTED + PROCESSING → both handled correctly
 */
@ExtendWith(MockitoExtension.class)
class KycQueueRecoveryServiceTest {

    @Mock
    private KycRequestRepository repository;

    @Mock
    private KycQueueService queueService;

    @InjectMocks
    private KycQueueRecoveryService recoveryService;

    private KycRequest submittedRequest;
    private KycRequest processingRequest;

    @BeforeEach
    void setUp() {
        submittedRequest = new KycRequest();
        submittedRequest.setId(1L);
        submittedRequest.setStatus(KycStatus.SUBMITTED.name());

        processingRequest = new KycRequest();
        processingRequest.setId(2L);
        processingRequest.setStatus(KycStatus.PROCESSING.name());
    }

    @Test
    @DisplayName("No stuck requests → queue is not touched")
    void recoverStuckRequests_NoStuck_NothingQueued() {
        when(repository.findByStatus(KycStatus.SUBMITTED.name()))
                .thenReturn(Collections.emptyList());
        when(repository.findByStatus(KycStatus.PROCESSING.name()))
                .thenReturn(Collections.emptyList());

        recoveryService.recoverStuckRequests();

        verifyNoInteractions(queueService);
    }

    @Test
    @DisplayName("SUBMITTED requests are re-queued without status change")
    void recoverStuckRequests_SubmittedOnly_ReQueued() {
        when(repository.findByStatus(KycStatus.SUBMITTED.name()))
                .thenReturn(List.of(submittedRequest));
        when(repository.findByStatus(KycStatus.PROCESSING.name()))
                .thenReturn(Collections.emptyList());

        recoveryService.recoverStuckRequests();

        verify(queueService).push(1L);
        verifyNoMoreInteractions(queueService);
        // Status should remain SUBMITTED
        assert submittedRequest.getStatus().equals(KycStatus.SUBMITTED.name());
    }

    @Test
    @DisplayName("PROCESSING requests are reset to SUBMITTED and re-queued")
    void recoverStuckRequests_ProcessingOnly_ResetAndReQueued() {
        when(repository.findByStatus(KycStatus.SUBMITTED.name()))
                .thenReturn(Collections.emptyList());
        when(repository.findByStatus(KycStatus.PROCESSING.name()))
                .thenReturn(List.of(processingRequest));

        recoveryService.recoverStuckRequests();

        verify(queueService).push(2L);
        verifyNoMoreInteractions(queueService);
        // Status should be reset to SUBMITTED
        assert processingRequest.getStatus().equals(KycStatus.SUBMITTED.name());
    }

    @Test
    @DisplayName("Mixed SUBMITTED + PROCESSING → both handled correctly")
    void recoverStuckRequests_Mixed_AllRecovered() {
        when(repository.findByStatus(KycStatus.SUBMITTED.name()))
                .thenReturn(List.of(submittedRequest));
        when(repository.findByStatus(KycStatus.PROCESSING.name()))
                .thenReturn(List.of(processingRequest));

        recoveryService.recoverStuckRequests();

        verify(queueService).push(1L);
        verify(queueService).push(2L);
        verifyNoMoreInteractions(queueService);
        // Submitted stays SUBMITTED, Processing reset to SUBMITTED
        assert submittedRequest.getStatus().equals(KycStatus.SUBMITTED.name());
        assert processingRequest.getStatus().equals(KycStatus.SUBMITTED.name());
    }
}

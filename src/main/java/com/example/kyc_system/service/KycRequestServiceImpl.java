package com.example.kyc_system.service;

import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.KycRequestRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class KycRequestServiceImpl implements KycRequestService {

    private final KycRequestRepository repository;
    private final UserService userService;

    @Override
    public KycRequest createOrReuse(Long userId) {
        LocalDateTime startOfDay = LocalDateTime.now().with(java.time.LocalTime.MIN);
        long dailyCount = repository.countByUserIdAndSubmittedAtGreaterThanEqual(userId, startOfDay);

        if (dailyCount >= 5) {
            throw new RuntimeException("Daily KYC request limit reached. You can only make 5 requests per day.");
        }

        Optional<KycRequest> latestRequest = repository.findTopByUserIdOrderByCreatedAtDesc(userId);

        if (latestRequest.isPresent()) {
            KycRequest request = latestRequest.get();
            String status = request.getStatus();

            if (status.equals(KycStatus.PENDING.name()) ||
                    status.equals(KycStatus.SUBMITTED.name()) ||
                    status.equals(KycStatus.PROCESSING.name())) {
                throw new RuntimeException(
                        "Only one KYC request can be processed at a time. Please wait until your current request is completed.");
            }

            if (status.equals(KycStatus.FAILED.name())) {
                request.setAttemptNumber(request.getAttemptNumber() + 1);
                request.setStatus(KycStatus.SUBMITTED.name());
                request.setSubmittedAt(LocalDateTime.now());
                return request;
            }
        }

        User user = userService.getActiveUser(userId);
        KycRequest newRequest = new KycRequest();
        newRequest.setUser(user);
        newRequest.setStatus(KycStatus.SUBMITTED.name());
        newRequest.setAttemptNumber(1);
        newRequest.setSubmittedAt(LocalDateTime.now());
        return repository.save(newRequest);
    }

    @Override
    public void updateStatus(Long requestId, KycStatus status) {
        KycRequest request = repository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("KYC request not found"));
        request.setStatus(String.valueOf(status));
    }

    @Override
    public Optional<KycRequest> getLatestByUser(Long userId) {
        return repository.findTopByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public java.util.List<KycRequest> getAllByUser(Long userId) {
        return repository.findByUserId(userId);
    }
}

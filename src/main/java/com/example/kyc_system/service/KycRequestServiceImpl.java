package com.example.kyc_system.service;

import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.KycRequestRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class KycRequestServiceImpl implements KycRequestService {

    private final KycRequestRepository repository;
    private final UserService userService;

    @Override
    public KycRequest createOrReuse(Long userId) {

        return repository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .filter(r -> r.getStatus().equals(KycStatus.FAILED.name()))
                .map(r -> {
                    r.setAttemptNumber(r.getAttemptNumber() + 1);
                    r.setStatus(KycStatus.SUBMITTED.name());
                    return r;
                })
                .orElseGet(() -> {
                    User user = userService.getActiveUser(userId);
                    KycRequest newRequest = new KycRequest();
                    newRequest.setUser(user);
                    newRequest.setStatus(KycStatus.SUBMITTED.name());
                    newRequest.setAttemptNumber(1);
                    return repository.save(newRequest);
                });
    }

    @Override
    public void updateStatus(Long requestId, KycStatus status) {
        KycRequest request = repository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("KYC request not found"));
        request.setStatus(String.valueOf(status));
    }

    @Override
    public Optional<KycRequest> getLatestByUser(Long userId) {
        return Optional.empty();
    }
}

package com.example.kyc_system.service;

import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.enums.KycStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.kyc_system.dto.KycRequestSearchDTO;

import java.util.Optional;

public interface KycRequestService {
    KycRequest createOrReuse(Long userId, String documentType);

    void updateStatus(Long requestId, KycStatus status);

    Optional<KycRequest> getLatestByUser(Long userId);

    java.util.List<KycRequest> getAllByUser(Long userId);

    Page<KycRequest> searchKycRequests(KycRequestSearchDTO searchDTO, Pageable pageable);
}

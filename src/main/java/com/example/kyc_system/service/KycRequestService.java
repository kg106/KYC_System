package com.example.kyc_system.service;

import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.enums.KycStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.kyc_system.dto.KycReportDataDTO;
import com.example.kyc_system.dto.KycRequestSearchDTO;

import java.util.Optional;
import java.time.LocalDate;
import java.util.*;

public interface KycRequestService {
    KycRequest createOrReuse(Long userId, String documentType);

    void updateStatus(Long requestId, KycStatus status);

    Optional<KycRequest> getLatestByUser(Long userId);

    List<KycRequest> getAllByUser(Long userId);

    Page<KycRequest> searchKycRequests(KycRequestSearchDTO searchDTO, Pageable pageable);

    List<KycReportDataDTO> getReportData(LocalDate dateFrom, LocalDate dateTo);
}

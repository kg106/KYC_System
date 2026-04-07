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

/**
 * Service interface for KYC Request lifecycle management.
 * Handles creation, status updates, and reporting for KYC transactions.
 */
public interface KycRequestService {
    /**
     * Creates a new KYC request or reuses an existing one if it's in a terminable state.
     *
     * @param userId user ID
     * @param documentType type of document
     * @return the KycRequest entity
     */
    KycRequest createOrReuse(String userId, String documentType);

    /**
     * Updates the status of a KYC request.
     *
     * @param requestId request ID
     * @param status new status
     */
    void updateStatus(Long requestId, KycStatus status);

    /**
     * Gets the most recent KYC request for a user.
     *
     * @param userId user ID
     * @return optional KycRequest
     */
    Optional<KycRequest> getLatestByUser(String userId);

    /**
     * Retrieves all KYC requests for a user.
     *
     * @param userId user ID
     * @return list of KycRequests
     */
    List<KycRequest> getAllByUser(String userId);

    /**
     * Searches KYC requests with filters and pagination.
     *
     * @param searchDTO filters
     * @param pageable pagination
     * @return paged KycRequests
     */
    Page<KycRequest> searchKycRequests(KycRequestSearchDTO searchDTO, Pageable pageable);

    /**
     * Extracts reporting data for a specific date range.
     *
     * @param dateFrom start date
     * @param dateTo end date
     * @return list of report data rows
     */
    List<KycReportDataDTO> getReportData(LocalDate dateFrom, LocalDate dateTo, String tenantId);
}

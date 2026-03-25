package com.example.kyc_system.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.kyc_system.service.KycReportService;
import com.example.kyc_system.service.KycRequestService;
import org.springframework.stereotype.Service;

import com.example.kyc_system.dto.KycMonthlyReportDTO;
import com.example.kyc_system.dto.KycReportDataDTO;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of KycReportService.
 * Aggregates KYC request data, status counts, and user registration metrics
 * to provide a comprehensive monthly report.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KycReportServiceImpl implements KycReportService {

        private final KycRequestRepository kycRequestRepository;
        private final KycRequestService requestService;
        private final UserRepository userRepository;

        /**
         * Generates a structural monthly report DTO with aggregated statistics.
         *
         * @param dateFrom start date
         * @param dateTo end date
         * @return KycMonthlyReportDTO with stats and detailed breakdown
         */
        @Override
        public KycMonthlyReportDTO generateMonthlyReport(LocalDate dateFrom, LocalDate dateTo, String tenantId) {
                log.info("Generating monthly report: from={} to={} tenantId={}", dateFrom, dateTo, tenantId);
                LocalDateTime from = dateFrom.atStartOfDay();
                LocalDateTime to = dateTo.atTime(23, 59, 59);

                // Status breakdown: fetching [status, count] pairs from repo
                Map<String, Long> statusMap = new HashMap<>();
                if (tenantId != null) {
                        kycRequestRepository.countByStatusBetween(tenantId, from, to)
                                        .forEach(row -> statusMap.put((String) row[0], (Long) row[1]));
                } else {
                        kycRequestRepository.countByStatusBetween(from, to)
                                        .forEach(row -> statusMap.put((String) row[0], (Long) row[1]));
                }

                long verified = statusMap.getOrDefault("VERIFIED", 0L);
                long failed = statusMap.getOrDefault("FAILED", 0L);
                long pending = statusMap.getOrDefault("PENDING", 0L) + statusMap.getOrDefault("SUBMITTED", 0L)
                                + statusMap.getOrDefault("PROCESSING", 0L);
                long total = verified + failed + pending;

                double passRate = total > 0 ? (double) verified / total * 100 : 0.0;

                // Document type breakdown: fetching [type, count] pairs
                Map<String, Long> docTypeMap = new LinkedHashMap<>();
                if (tenantId != null) {
                        kycRequestRepository.countByDocumentTypeBetween(tenantId, from, to)
                                        .forEach(row -> docTypeMap.put((String) row[0], (Long) row[1]));
                } else {
                        kycRequestRepository.countByDocumentTypeBetween(from, to)
                                        .forEach(row -> docTypeMap.put((String) row[0], (Long) row[1]));
                }

                // User registration stats
                long newUsers;
                long totalUsers;
                if (tenantId != null) {
                        newUsers = userRepository.countNewUsersBetween(tenantId, from, to);
                        totalUsers = userRepository.countByTenantId(tenantId);
                } else {
                        newUsers = userRepository.countNewUsersBetween(from, to);
                        totalUsers = userRepository.count();
                }

                // Detailed data list from RequestService
                List<KycReportDataDTO> reportData = requestService.getReportData(dateFrom, dateTo, tenantId);

                log.info("Monthly report generated: totalRequests={}, passRate={}%", total,
                                String.format("%.2f", passRate));

                return KycMonthlyReportDTO.builder()
                                .dateFrom(dateFrom)
                                .dateTo(dateTo)
                                .totalRequests(total)
                                .verified(verified)
                                .failed(failed)
                                .pending(pending)
                                .passRate(passRate)
                                .breakdownByDocumentType(docTypeMap)
                                .newUsersRegistered(newUsers)
                                .totalActiveUsers(totalUsers)
                                .kycData(reportData)
                                .build();
        }
}

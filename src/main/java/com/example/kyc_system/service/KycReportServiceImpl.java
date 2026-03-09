package com.example.kyc_system.service;

//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.kyc_system.dto.KycMonthlyReportDTO;
import com.example.kyc_system.dto.KycReportDataDTO;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// service/impl/KycReportServiceImpl.java
@Service
@RequiredArgsConstructor
@Slf4j
public class KycReportServiceImpl implements KycReportService {

        private final KycRequestRepository kycRequestRepository;
        private final KycRequestService requestService;
        private final UserRepository userRepository;

        @Override
        public KycMonthlyReportDTO generateMonthlyReport(LocalDate dateFrom, LocalDate dateTo) {
                LocalDateTime from = dateFrom.atStartOfDay();
                LocalDateTime to = dateTo.atTime(23, 59, 59);

                // Status breakdown
                Map<String, Long> statusMap = new HashMap<>();
                kycRequestRepository.countByStatusBetween(from, to)
                                .forEach(row -> statusMap.put((String) row[0], (Long) row[1]));

                long verified = statusMap.getOrDefault("VERIFIED", 0L);
                long failed = statusMap.getOrDefault("FAILED", 0L);
                long pending = statusMap.getOrDefault("PENDING", 0L) + statusMap.getOrDefault("SUBMITTED", 0L)
                                + statusMap.getOrDefault("PROCESSING", 0L);
                long total = verified + failed + pending;

                double passRate = total > 0 ? (double) verified / total * 100 : 0.0;

                // Document type breakdown
                Map<String, Long> docTypeMap = new LinkedHashMap<>();
                kycRequestRepository.countByDocumentTypeBetween(from, to)
                                .forEach(row -> docTypeMap.put((String) row[0], (Long) row[1]));

                // User stats
                long newUsers = userRepository.countNewUsersBetween(from, to);
                long totalUsers = userRepository.count();

                List<KycReportDataDTO> reportData = requestService.getReportData(dateFrom, dateTo);

                return KycMonthlyReportDTO.builder()
                                .dateFrom(dateFrom)
                                .dateTo(dateTo)
                                .totalRequests(total)
                                .verified(verified)// Fixed IV for deterministic encryption
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

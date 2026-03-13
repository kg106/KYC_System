package com.example.kyc_system.service;

import com.example.kyc_system.dto.KycMonthlyReportDTO;
import com.example.kyc_system.dto.KycReportDataDTO;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KycReportServiceImpl Unit Tests")
class KycReportServiceImplTest {

    @Mock
    private KycRequestRepository kycRequestRepository;

    @Mock
    private KycRequestService requestService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private KycReportServiceImpl reportService;

    private LocalDate dateFrom;
    private LocalDate dateTo;

    @BeforeEach
    void setUp() {
        dateFrom = LocalDate.of(2023, 1, 1);
        dateTo = LocalDate.of(2023, 1, 31);
    }

    @Nested
    @DisplayName("generateMonthlyReport()")
    class GenerateMonthlyReportTests {

        @Test
        @DisplayName("Should generate accurate report with correct aggregations and pass rates")
        void generateMonthlyReport_WithData_CalculatesCorrectly() {
            // Mock Status Data
            List<Object[]> statusData = List.of(
                    new Object[] { "VERIFIED", 70L },
                    new Object[] { "FAILED", 20L },
                    new Object[] { "PENDING", 5L },
                    new Object[] { "PROCESSING", 5L });
            when(kycRequestRepository.countByStatusBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(statusData);

            // Mock Document Type Data
            List<Object[]> docTypeData = List.of(
                    new Object[] { "PAN", 60L },
                    new Object[] { "AADHAAR", 40L });
            when(kycRequestRepository.countByDocumentTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(docTypeData);

            // Mock User Data
            when(userRepository.countNewUsersBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(15L);
            when(userRepository.count()).thenReturn(150L);

            // Mock Detailed Data
            List<KycReportDataDTO> reportDataList = List.of(
                    KycReportDataDTO.builder().userId(1L).userName("User1").status("VERIFIED").build());
            when(requestService.getReportData(eq(dateFrom), eq(dateTo))).thenReturn(reportDataList);

            // Execute
            KycMonthlyReportDTO report = reportService.generateMonthlyReport(dateFrom, dateTo);

            // Assert
            assertNotNull(report);
            assertEquals(dateFrom, report.getDateFrom());
            assertEquals(dateTo, report.getDateTo());
            assertEquals(100L, report.getTotalRequests()); // 70 + 20 + 5 + 5
            assertEquals(70L, report.getVerified());
            assertEquals(20L, report.getFailed());
            assertEquals(10L, report.getPending()); // PENDING + PROCESSING = 5 + 5
            assertEquals(70.0, report.getPassRate(), 0.01);

            assertEquals(2, report.getBreakdownByDocumentType().size());
            assertEquals(60L, report.getBreakdownByDocumentType().get("PAN"));
            assertEquals(40L, report.getBreakdownByDocumentType().get("AADHAAR"));

            assertEquals(15L, report.getNewUsersRegistered());
            assertEquals(150L, report.getTotalActiveUsers());
            assertEquals(1, report.getKycData().size());
            assertEquals("User1", report.getKycData().get(0).getUserName());
        }

        @Test
        @DisplayName("Should return 0 pass rate when total requests is 0")
        void generateMonthlyReport_EmptyData_ReturnsZeroPassRate() {
            // Mock Empty Status Data
            when(kycRequestRepository.countByStatusBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            // Mock Empty Document Type Data
            when(kycRequestRepository.countByDocumentTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            when(userRepository.countNewUsersBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(0L);
            when(userRepository.count()).thenReturn(50L);
            when(requestService.getReportData(eq(dateFrom), eq(dateTo))).thenReturn(List.of());

            // Execute
            KycMonthlyReportDTO report = reportService.generateMonthlyReport(dateFrom, dateTo);

            // Assert
            assertNotNull(report);
            assertEquals(0L, report.getTotalRequests());
            assertEquals(0L, report.getVerified());
            assertEquals(0.0, report.getPassRate(), 0.01);
            assertEquals(50L, report.getTotalActiveUsers());
        }
    }
}

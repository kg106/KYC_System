package com.example.kyc_system.scheduler;

import com.example.kyc_system.dto.KycMonthlyReportDTO;
import com.example.kyc_system.service.KycReportService;
import com.example.kyc_system.service.impl.KycReportEmailServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
// import com.example.kyc_system.dto.*;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KycReportScheduler Unit Tests")
class KycReportSchedulerTest {

    @Mock
    private KycReportService reportService;
    @Mock
    private KycReportEmailServiceImpl emailService;

    @InjectMocks
    private KycReportScheduler scheduler;

    // ─── sendMonthlyReport() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("sendMonthlyReport()")
    class SendMonthlyReportTests {

        @Test
        @DisplayName("Should call reportService with last month's date range")
        void sendMonthlyReport_CallsReportServiceWithLastMonthRange() {
            YearMonth lastMonth = YearMonth.now().minusMonths(1);
            LocalDate expectedFrom = lastMonth.atDay(1);
            LocalDate expectedTo = lastMonth.atEndOfMonth();

            KycMonthlyReportDTO mockReport = new KycMonthlyReportDTO();
            when(reportService.generateMonthlyReport(eq(expectedFrom), eq(expectedTo), any())).thenReturn(mockReport);

            scheduler.sendMonthlyReport();

            ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(reportService).generateMonthlyReport(fromCaptor.capture(), toCaptor.capture(), any());

            assertEquals(expectedFrom, fromCaptor.getValue(),
                    "dateFrom must be the first day of last month");
            assertEquals(expectedTo, toCaptor.getValue(),
                    "dateTo must be the last day of last month");
        }

        @Test
        @DisplayName("Should pass generated report to emailService for sending")
        void sendMonthlyReport_PassesReportToEmailService() {
            KycMonthlyReportDTO mockReport = new KycMonthlyReportDTO();
            when(reportService.generateMonthlyReport(any(), any(), any())).thenReturn(mockReport);

            scheduler.sendMonthlyReport();

            verify(emailService).sendMonthlyReport(eq(mockReport), any());
        }

        @Test
        @DisplayName("Should call reportService exactly once per invocation")
        void sendMonthlyReport_CallsReportServiceExactlyOnce() {
            when(reportService.generateMonthlyReport(any(), any(), any())).thenReturn(new KycMonthlyReportDTO());

            scheduler.sendMonthlyReport();

            verify(reportService, times(1)).generateMonthlyReport(any(), any(), any());
            verify(emailService, times(1)).sendMonthlyReport(any(), any());
        }
    }

    // ─── triggerManually() ────────────────────────────────────────────────────

    @Nested
    @DisplayName("triggerManually(dateFrom, dateTo)")
    class TriggerManuallyTests {

        @Test
        @DisplayName("Should use the provided dateFrom and dateTo exactly")
        void triggerManually_UsesProvidedDates() {
            LocalDate from = LocalDate.of(2025, 1, 1);
            LocalDate to = LocalDate.of(2025, 1, 31);
            KycMonthlyReportDTO mockReport = new KycMonthlyReportDTO();

            when(reportService.generateMonthlyReport(eq(from), eq(to), any())).thenReturn(mockReport);

            scheduler.triggerManually(from, to, null, null);

            verify(reportService).generateMonthlyReport(eq(from), eq(to), any());
            verify(emailService).sendMonthlyReport(eq(mockReport), any());
        }

        @Test
        @DisplayName("Should pass generated report to emailService")
        void triggerManually_PassesReportToEmailService() {
            LocalDate from = LocalDate.of(2024, 11, 1);
            LocalDate to = LocalDate.of(2024, 11, 30);
            KycMonthlyReportDTO mockReport = new KycMonthlyReportDTO();

            when(reportService.generateMonthlyReport(eq(from), eq(to), any())).thenReturn(mockReport);

            scheduler.triggerManually(from, to, null, null);

            verify(emailService).sendMonthlyReport(eq(mockReport), any());
        }

        @Test
        @DisplayName("Should not throw even if report has no data (empty DTO)")
        void triggerManually_EmptyReport_NoException() {
            LocalDate from = LocalDate.of(2025, 3, 1);
            LocalDate to = LocalDate.of(2025, 3, 31);

            when(reportService.generateMonthlyReport(eq(from), eq(to), any())).thenReturn(new KycMonthlyReportDTO());

            assertDoesNotThrow(() -> scheduler.triggerManually(from, to, null, null));
        }

        @Test
        @DisplayName("Should propagate exception from reportService if generation fails")
        void triggerManually_ReportServiceThrows_PropagatesException() {
            LocalDate from = LocalDate.of(2025, 3, 1);
            LocalDate to = LocalDate.of(2025, 3, 31);

            when(reportService.generateMonthlyReport(eq(from), eq(to), any()))
                    .thenThrow(new RuntimeException("Failed to fetch report data"));

            assertThrows(RuntimeException.class, () -> scheduler.triggerManually(from, to, null, null));
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("triggerManually should call reportService exactly once")
        void triggerManually_CallsServiceExactlyOnce() {
            LocalDate from = LocalDate.of(2025, 2, 1);
            LocalDate to = LocalDate.of(2025, 2, 28);

            when(reportService.generateMonthlyReport(eq(from), eq(to), any())).thenReturn(new KycMonthlyReportDTO());

            scheduler.triggerManually(from, to, null, null);

            verify(reportService, times(1)).generateMonthlyReport(eq(from), eq(to), any());
            verify(emailService, times(1)).sendMonthlyReport(any(), any());
        }
    }
}
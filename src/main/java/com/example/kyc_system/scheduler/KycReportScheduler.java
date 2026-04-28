package com.example.kyc_system.scheduler;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.kyc_system.dto.KycMonthlyReportDTO;
import com.example.kyc_system.service.KycReportService;
import com.example.kyc_system.service.impl.KycReportEmailServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// scheduler/KycReportScheduler.java
@Component
@RequiredArgsConstructor
@Slf4j
public class KycReportScheduler {

    private final KycReportService reportService;
    private final KycReportEmailServiceImpl emailService;

    // Runs at 8:00 AM on the 1st of every month
    /**
     * Schedules the automatic generation and email dispatch of the monthly KYC report.
     * Runs at 8:00 AM on the 1st of every month (Asia/Kolkata timezone).
     *
     * The report covers all activity from the previous calendar month.
     */
    @Scheduled(cron = "0 0 8 1 * *", zone = "Asia/Kolkata")
    public void sendMonthlyReport() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        LocalDate dateFrom = lastMonth.atDay(1);
        LocalDate dateTo = lastMonth.atEndOfMonth();
        log.info("Generating KYC monthly report for {}", lastMonth);
        KycMonthlyReportDTO report = reportService.generateMonthlyReport(dateFrom, dateTo, null);
        emailService.sendMonthlyReport(report, null);
    }

    // Optional: Admin can trigger manually via endpoint
    /**
     * Manually triggers a report generation for a custom date range.
     * Useful for auditing or ad-hoc reporting requests.
     *
     * @param dateFrom       start date (inclusive)
     * @param dateTo         end date (inclusive)
     * @param tenantId       optional tenant filtering
     * @param recipientEmail optional custom email address
     */
    public void triggerManually(LocalDate dateFrom, LocalDate dateTo, String tenantId, String recipientEmail) {
        log.info("Manual report trigger requested: from={} to={} tenantId={} email={}", dateFrom, dateTo, tenantId,
                recipientEmail);
        KycMonthlyReportDTO report = reportService.generateMonthlyReport(dateFrom, dateTo, tenantId);
        emailService.sendMonthlyReport(report, recipientEmail);
    }
}
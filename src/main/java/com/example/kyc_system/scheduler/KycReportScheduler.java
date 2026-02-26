package com.example.kyc_system.scheduler;

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
    @Scheduled(cron = "0 0 8 1 * *", zone = "Asia/Kolkata")
    public void sendMonthlyReport() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        log.info("Generating KYC monthly report for {}", lastMonth);
        KycMonthlyReportDTO report = reportService.generateMonthlyReport(lastMonth);
        emailService.sendMonthlyReport(report);
    }

    // Optional: Admin can trigger manually via endpoint
    public void triggerManually(YearMonth month) {
        KycMonthlyReportDTO report = reportService.generateMonthlyReport(month);
        emailService.sendMonthlyReport(report);
    }
}
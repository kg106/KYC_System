package com.example.kyc_system.service;

import java.time.YearMonth;

import com.example.kyc_system.dto.KycMonthlyReportDTO;

// service/KycReportService.java
public interface KycReportService {
    KycMonthlyReportDTO generateMonthlyReport(YearMonth month);
}

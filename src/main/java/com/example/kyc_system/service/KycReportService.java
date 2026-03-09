package com.example.kyc_system.service;

import java.time.LocalDate;
//import java.time.YearMonth;

import com.example.kyc_system.dto.KycMonthlyReportDTO;

/** Service interface for generating aggregate monthly KYC reports. */
public interface KycReportService {
    KycMonthlyReportDTO generateMonthlyReport(LocalDate dateFrom, LocalDate dateTo);
}

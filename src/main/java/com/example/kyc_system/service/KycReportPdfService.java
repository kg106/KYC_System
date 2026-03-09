package com.example.kyc_system.service;

import com.example.kyc_system.dto.KycMonthlyReportDTO;

/**
 * Service interface for generating PDF reports.
 */
public interface KycReportPdfService {
    byte[] generateReportPdf(KycMonthlyReportDTO report);
}

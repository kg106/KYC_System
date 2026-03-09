package com.example.kyc_system.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.kyc_system.dto.KycMonthlyReportDTO;
import com.example.kyc_system.dto.KycReportDataDTO;
import com.example.kyc_system.service.impl.KycReportPdfServiceImpl;

public class KycReportPdfServiceImplTest {

    private final KycReportPdfServiceImpl pdfService = new KycReportPdfServiceImpl();

    @Test
    public void testGenerateReportPdf() {
        KycMonthlyReportDTO report = KycMonthlyReportDTO.builder()
                .dateFrom(LocalDate.of(2023, 1, 1))
                .dateTo(LocalDate.of(2023, 1, 31))
                .totalRequests(100)
                .verified(80)
                .failed(15)
                .pending(5)
                .passRate(80.0)
                .breakdownByDocumentType(Map.of("PAN", 50L, "AADHAAR", 50L))
                .newUsersRegistered(20)
                .totalActiveUsers(1000)
                .kycData(Collections.singletonList(
                        KycReportDataDTO.builder()
                                .userId(1L)
                                .userName("John Doe")
                                .status("VERIFIED")
                                .documentType("PAN")
                                .attemptNumber(1)
                                .build()))
                .build();

        byte[] pdfBytes = pdfService.generateReportPdf(report);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        // Basic PDF header Check
        String pdfContent = new String(pdfBytes, 0, Math.min(pdfBytes.length, 10));
        assertTrue(pdfContent.contains("%PDF-1."));
    }
}

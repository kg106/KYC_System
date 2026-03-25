package com.example.kyc_system.service.impl;

import com.example.kyc_system.dto.KycMonthlyReportDTO;
import com.example.kyc_system.service.KycReportPdfService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KycReportEmailServiceImpl Unit Tests")
class KycReportEmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private KycReportPdfService pdfService;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private KycReportEmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "recipients", "admin@test.com,manager@test.com");
        ReflectionTestUtils.setField(emailService, "senderEmail", "noreply@test.com");
    }

    @Test
    @DisplayName("Should generate PDF, create email, and send via JavaMailSender")
    void sendMonthlyReport_Success() {
        // Arrange
        KycMonthlyReportDTO report = KycMonthlyReportDTO.builder()
                .dateFrom(LocalDate.of(2023, 1, 1))
                .dateTo(LocalDate.of(2023, 1, 31))
                .totalRequests(100L)
                .verified(70L)
                .failed(20L)
                .pending(10L)
                .passRate(70.0)
                .breakdownByDocumentType(Map.of("PAN", 60L, "AADHAAR", 40L))
                .newUsersRegistered(15L)
                .totalActiveUsers(150L)
                .kycData(List.of())
                .build();

        byte[] mockPdfBytes = "%PDF-1.4 Mock Content".getBytes();
        when(pdfService.generateReportPdf(report)).thenReturn(mockPdfBytes);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        emailService.sendMonthlyReport(report, null);

        // Assert
        verify(pdfService).generateReportPdf(report);
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should gracefully handle exceptions and not throw errors up the stack")
    void sendMonthlyReport_ThrowsException_HandledGracefully() {
        // Arrange
        KycMonthlyReportDTO report = KycMonthlyReportDTO.builder()
                .dateFrom(LocalDate.of(2023, 1, 1))
                .dateTo(LocalDate.of(2023, 1, 31))
                .build();

        // Simulate an exception during PDF generation
        when(pdfService.generateReportPdf(report)).thenThrow(new RuntimeException("PDF Generation Failed"));

        // Act
        emailService.sendMonthlyReport(report, null);

        // Assert
        verify(pdfService).generateReportPdf(report);
        // Mail sender should not be called because PDF generation failed
        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}

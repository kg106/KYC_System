package com.example.kyc_system.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.kyc_system.dto.KycMonthlyReportDTO;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// service/impl/KycReportEmailServiceImpl.java
@Service
@RequiredArgsConstructor
@Slf4j
public class KycReportEmailServiceImpl {

  private final JavaMailSender mailSender;
  private final com.example.kyc_system.service.KycReportPdfService pdfService;

  @Value("${kyc.report.recipients}") // comma-separated emails
  private String recipients;

  @Value("${spring.mail.username}")
  private String senderEmail;

  @Async
  public void sendMonthlyReport(KycMonthlyReportDTO report) {
    try {
      log.info("Generating PDF for report range: {} to {}", report.getDateFrom(), report.getDateTo());
      byte[] pdfContent = pdfService.generateReportPdf(report);

      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom(senderEmail);
      helper.setTo(recipients.split(","));
      helper.setSubject(String.format("📊 KYC Report - [%s to %s]",
          report.getDateFrom(), report.getDateTo()));

      String emailBody = buildHtmlBody(report);
      helper.setText(emailBody, true);

      // Attach PDF
      String filename = String.format("KYC_Report_%s_to_%s.pdf",
          report.getDateFrom(), report.getDateTo());
      helper.addAttachment(filename, new org.springframework.core.io.ByteArrayResource(pdfContent));

      mailSender.send(message);
      log.info("KYC report email sent with PDF attachment for range: {} to {}",
          report.getDateFrom(), report.getDateTo());

    } catch (Exception e) {
      log.error("Failed to send monthly KYC report with PDF: {}", e.getMessage(), e);
    }
  }

  private String buildHtmlBody(KycMonthlyReportDTO r) {
    String dateRange = String.format("%s to %s", r.getDateFrom(), r.getDateTo());

    return String.format("""
        <html>
        <body style="font-family:Arial,sans-serif;color:#333;max-width:600px;margin:auto;padding:20px;">
          <div style="background:#1a73e8;padding:24px;border-radius:8px;color:#fff;text-align:center;">
            <h1 style="margin:0;font-size:22px;">📊 KYC Report Ready</h1>
            <p style="margin:4px 0 0;color:#cce5ff;">Reporting Period: %s</p>
          </div>

          <div style="padding:24px;line-height:1.6;">
            <p>Hello,</p>
            <p>The monthly KYC processing report for the period <b>%s</b> has been generated.</p>
            <p>Please find the detailed report attached as a PDF file.</p>

            <div style="margin:30px 0;padding:15px;background:#f8f9fa;border-left:4px solid #1a73e8;">
              <b>Summary Highlights:</b><br/>
              • Total Requests: %d<br/>
              • Passed: %d<br/>
              • Failed: %d<br/>
              • Pass Rate: <b>%.1f%%</b>
            </div>

            <p>If you have any questions regarding this report, please contact the system administrator.</p>
            <br/>
            <p style="color:#999;font-size:12px;">This is an automated message. Please do not reply.</p>
          </div>
        </body>
        </html>
        """,
        dateRange,
        dateRange,
        r.getTotalRequests(),
        r.getVerified(),
        r.getFailed(),
        r.getPassRate());
  }

}

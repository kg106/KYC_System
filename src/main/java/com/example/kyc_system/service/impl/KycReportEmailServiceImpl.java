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

  @Value("${kyc.report.recipients}") // comma-separated emails
  private String recipients;

  @Value("${spring.mail.username}")
  private String senderEmail;

  @Async
  public void sendMonthlyReport(KycMonthlyReportDTO report) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom(senderEmail);
      helper.setTo(recipients.split(","));
      helper.setSubject(String.format("📊 KYC Report - [%s to %s]",
          report.getDateFrom(), report.getDateTo()));
      helper.setText(buildHtmlBody(report), true); // true = isHtml

      mailSender.send(message);
      log.info("KYC report sent successfully for range: {} to {}",
          report.getDateFrom(), report.getDateTo());

    } catch (Exception e) {
      log.error("Failed to send monthly KYC report: {}", e.getMessage(), e);
    }
  }

  private String buildHtmlBody(KycMonthlyReportDTO r) {
    String dateRange = String.format("%s to %s", r.getDateFrom(), r.getDateTo());

    StringBuilder docRows = new StringBuilder();
    r.getBreakdownByDocumentType().forEach((type, count) -> docRows.append(String.format("""
        <tr>
        <td style="padding:8px 12px; border-bottom:1px solid #eee;">%s</td>
        <td style="padding:8px 12px; border-bottom:1px solid #eee;
        text-align:center;">%d</td>
        </tr>
        """, type, count)));

    StringBuilder kycDataRows = new StringBuilder();
    r.getKycData().forEach(d -> {
      String statusColor = "#333";
      if ("VERIFIED".equals(d.getStatus()))
        statusColor = "#34a853";
      else if ("FAILED".equals(d.getStatus()))
        statusColor = "#ea4335";

      kycDataRows.append(String.format("""
          <tr>
          <td style="padding:8px 12px; border-bottom:1px solid #eee;">%d</td>
          <td style="padding:8px 12px; border-bottom:1px solid #eee;"><b>%s</b></td>
          <td style="padding:8px 12px; border-bottom:1px solid #eee;">%s</td>
          <td style="padding:8px 12px; border-bottom:1px solid #eee; text-align:center; color:%s;"><b>%s</b></td>
          <td style="padding:8px 12px; border-bottom:1px solid #eee;">%s</td>
          <td style="padding:8px 12px; border-bottom:1px solid #eee;">%s</td>
          <td style="padding:8px 12px; border-bottom:1px solid #eee;">%s</td>
          <td style="padding:8px 12px; border-bottom:1px solid #eee;">%s</td>
          <td style="padding:8px 12px; border-bottom:1px solid #eee; text-align:center;">%d</td>
          <td style="padding:8px 12px; border-bottom:1px solid #eee; font-size:11px;">%s</td>
          </tr>
          """,
          d.getUserId(),
          d.getUserName(),
          d.getDob() != null ? d.getDob().toString() : "-",
          statusColor, d.getStatus(),
          d.getMobileNumber() != null ? d.getMobileNumber() : "-",
          d.getEmail() != null ? d.getEmail() : "-",
          d.getTenantName() != null ? d.getTenantName() : "-",
          d.getDocumentType(),
          d.getAttemptNumber(),
          d.getDecisionReason() != null ? d.getDecisionReason() : "-"));
    });

    return String.format("""
        <html><body
        style="font-family:Arial,sans-serif;color:#333;max-width:800px;margin:auto;">
        <div style="background:#1a73e8;padding:24px;border-radius:8px 8px 0 0;">
        <h1 style="color:#fff;margin:0;font-size:22px;">📊 KYC Monthly Report</h1>
        <p style="color:#cce5ff;margin:4px 0 0;">%s</p>
        </div>

        <div style="background:#f9f9f9;padding:24px;">

        <!-- Summary Cards -->
        <div style="display:flex;gap:12px;margin-bottom:24px;">
        %s %s %s %s
        </div>

        <!-- Pass Rate -->
        <div
        style="background:#fff;border-radius:6px;padding:16px;margin-bottom:20px;
        border-left:4px solid #1a73e8;">
        <strong>Pass Rate:</strong>
        <span style="font-size:24px;color:#1a73e8;margin-left:8px;">%.1f%%</span>
        </div>

        <!-- Document Breakdown -->
        <h3 style="margin-bottom:8px;">Breakdown by Document Type</h3>
        <table
        style="width:100%%;border-collapse:collapse;background:#fff;border-radius:6px;">
        <thead>
        <tr style="background:#e8f0fe;">
        <th style="padding:10px 12px;text-align:left;">Document Type</th>
        <th style="padding:10px 12px;text-align:center;">Count</th>
        </tr>
        </thead>
        <tbody>%s</tbody>
        </table>

        <!-- User Stats -->
        <div style="background:#fff;border-radius:6px;padding:16px;margin-top:20px;">
        <strong>User Statistics</strong><br/>
        New Registrations: <b>%d</b> &nbsp;|&nbsp; Total Active Users: <b>%d</b>
        </div>

        <!-- Detailed KYC Report -->
        <h3 style="margin-top:24px;margin-bottom:8px;">Detailed KYC Report</h3>
        <div style="overflow-x:auto;-webkit-overflow-scrolling:touch;">
        <table
        style="width:100%%;border-collapse:collapse;background:#fff;border-radius:6px;font-size:11px;min-width:1000px;">
        <thead>
        <tr style="background:#e8f0fe;">
        <th style="padding:10px 12px;text-align:left;border-bottom:2px solid #1a73e8;">UID</th>
        <th style="padding:10px 12px;text-align:left;border-bottom:2px solid #1a73e8;">User Name</th>
        <th style="padding:10px 12px;text-align:left;border-bottom:2px solid #1a73e8;">DOB</th>
        <th style="padding:10px 12px;text-align:center;border-bottom:2px solid #1a73e8;">Status</th>
        <th style="padding:10px 12px;text-align:left;border-bottom:2px solid #1a73e8;">Mobile</th>
        <th style="padding:10px 12px;text-align:left;border-bottom:2px solid #1a73e8;">Email</th>
        <th style="padding:10px 12px;text-align:left;border-bottom:2px solid #1a73e8;">Tenant</th>
        <th style="padding:10px 12px;text-align:left;border-bottom:2px solid #1a73e8;">Doc Type</th>
        <th style="padding:10px 12px;text-align:center;border-bottom:2px solid #1a73e8;">Try</th>
        <th style="padding:10px 12px;text-align:left;border-bottom:2px solid #1a73e8;">Reason</th>
        </tr>
        </thead>
        <tbody>%s</tbody>
        </table>
        </div>

        <p style="color:#999;font-size:12px;margin-top:24px;">
        This is an automated report generated by the KYC System.
        </p>
        </div>
        </body></html>
        """,
        dateRange,
        summaryCard("Total", String.valueOf(r.getTotalRequests()), "#1a73e8"),
        summaryCard("✅ Passed", String.valueOf(r.getVerified()), "#34a853"),
        summaryCard("❌ Failed", String.valueOf(r.getFailed()), "#ea4335"),
        summaryCard("⏳ Pending", String.valueOf(r.getPending()), "#fbbc04"),
        r.getPassRate(),
        docRows,
        r.getNewUsersRegistered(),
        r.getTotalActiveUsers(),
        kycDataRows);
  }

  private String summaryCard(String label, String value, String color) {
    return String.format("""
            <div style="flex:1;background:#fff;border-radius:6px;padding:16px;
                        text-align:center;border-top:3px solid %s;">
              <div style="font-size:28px;font-weight:bold;color:%s;">%s</div>
              <div style="font-size:13px;color:#666;margin-top:4px;">%s</div>
            </div>
        """, color, color, value, label);
  }
}

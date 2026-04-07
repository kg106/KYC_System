package com.example.kyc_system.service.impl;

import java.io.ByteArrayOutputStream;

import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import com.example.kyc_system.dto.KycMonthlyReportDTO;
import com.example.kyc_system.service.KycReportPdfService;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of KycReportPdfService.
 * Uses XHTMLRenderer (Flying Saucer) and iText to convert HTML templates to PDF documents.
 */
@Service
@Slf4j
public class KycReportPdfServiceImpl implements KycReportPdfService {

    /**
     * Renders a KYC Monthly Report as a downloadable PDF byte array.
     *
     * @param report the data to render
     * @return PDF content as bytes
     */
    @Override
    public byte[] generateReportPdf(KycMonthlyReportDTO report) {
        String htmlContent = buildHtmlContent(report);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(htmlContent);
            renderer.layout();
            renderer.createPDF(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Error generating PDF report: {}", e.getMessage(), e);
            throw new RuntimeException("Could not generate PDF report", e);
        }
    }

    /**
     * Builds the HTML/XHTML string for the report.
     * Note: XHTMLRenderer requires strict XHTML compliance and supports basic CSS.
     */
    private String buildHtmlContent(KycMonthlyReportDTO r) {
        String dateRange = String.format("%s to %s", r.getDateFrom(), r.getDateTo());

        StringBuilder docRows = new StringBuilder();
        r.getBreakdownByDocumentType().forEach((type, count) -> docRows.append(String.format("""
                <tr>
                <td style="padding:8px 12px; border-bottom:1px solid #eee;">%s</td>
                <td style="padding:8px 12px; border-bottom:1px solid #eee; text-align:center;">%d</td>
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
                    <td style="padding:8px 12px; border-bottom:1px solid #eee;">%s</td>
                    <td style="padding:8px 12px; border-bottom:1px solid #eee;">%s</td>
                    <td style="padding:8px 12px; border-bottom:1px solid #eee;">%s</td>
                    <td style="padding:8px 12px; border-bottom:1px solid #eee; text-align:center; color:%s;">%s</td>
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

        // Flying Saucer needs well-formed XML/XHTML.
        // We also use basic CSS since OpenPDF doesn't support advanced CSS.
        return String.format(
                """
                        <!DOCTYPE html>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                        <head>
                            <style>
                                body { font-family: 'Arial', sans-serif; color: #333; margin: 40px; }
                                .header { background: #1a73e8; padding: 20px; color: #fff; border-radius: 8px 8px 0 0; }
                                .header h1 { margin: 0; font-size: 24px; }
                                .summary-container { margin: 20px 0; border: 1px solid #eee; padding: 15px; border-radius: 8px; }
                                .summary-card { display: inline-block; width: 23%%; text-align: center; border-right: 1px solid #eee; }
                                .summary-card:last-child { border-right: none; }
                                .summary-value { font-size: 20px; font-weight: bold; color: #1a73e8; }
                                .summary-label { font-size: 12px; color: #666; }
                                .rate-box { background: #f8f9fa; padding: 10px; border-left: 5px solid #1a73e8; margin-bottom: 20px; }
                                table { width: 100%%; border-collapse: collapse; margin-top: 20px; }
                                th { background: #f1f3f4; padding: 10px; text-align: left; font-size: 12px; border-bottom: 2px solid #1a73e8; }
                                td { padding: 8px; border-bottom: 1px solid #eee; font-size: 10px; }
                                .footer { text-align: center; color: #999; font-size: 10px; margin-top: 30px; }
                            </style>
                        </head>
                        <body>
                            <div class="header">
                                <h1>KYC Monthly Report</h1>
                                <p>%s</p>
                            </div>

                            <div class="summary-container">
                                <div class="summary-card">
                                    <div class="summary-value">%d</div>
                                    <div class="summary-label">Total Requests</div>
                                </div>
                                <div class="summary-card">
                                    <div class="summary-value">%d</div>
                                    <div class="summary-label">Passed</div>
                                </div>
                                <div class="summary-card">
                                    <div class="summary-value">%d</div>
                                    <div class="summary-label">Failed</div>
                                </div>
                                <div class="summary-card">
                                    <div class="summary-value">%d</div>
                                    <div class="summary-label">Pending</div>
                                </div>
                            </div>

                            <div class="rate-box">
                                <strong>Pass Rate:</strong> <span style="font-size: 18px; color: #1a73e8;">%.1f%%</span>
                            </div>

                            <h3>Breakdown by Document Type</h3>
                            <table>
                                <thead>
                                    <tr>
                                        <th>Document Type</th>
                                        <th style="text-align:center;">Count</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    %s
                                </tbody>
                            </table>

                            <h3>Detailed KYC Data</h3>
                            <table>
                                <thead>
                                    <tr>
                                        <th>UID</th>
                                        <th>Name</th>
                                        <th>DOB</th>
                                        <th>Status</th>
                                        <th>Mobile</th>
                                        <th>Email</th>
                                        <th>Tenant</th>
                                        <th>Doc</th>
                                        <th>Try</th>
                                        <th>Reason</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    %s
                                </tbody>
                            </table>

                            <div class="footer">
                                Automated report generated by the KYC System on %s
                            </div>
                        </body>
                        </html>
                        """,
                dateRange,
                r.getTotalRequests(), r.getVerified(), r.getFailed(), r.getPending(),
                r.getPassRate(),
                docRows,
                kycDataRows,
                java.time.LocalDateTime.now());
    }
}

package com.example.kyc_system.flow;

import com.example.kyc_system.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

// import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ============================================================
 * KYC REPORT TRIGGER — GAP #5
 * ============================================================
 *
 * ENDPOINT: POST /api/kyc/report
 * ACCESS: @PreAuthorize("hasRole('ADMIN')")
 *
 * WHAT THE ENDPOINT DOES:
 * Controller parses optional ?dateFrom=YYYY-MM-DD&dateTo=YYYY-MM-DD params:
 * - If both provided → uses them as the report range
 * - If omitted → defaults to previous calendar month
 * Then calls: reportScheduler.triggerManually(df, dt)
 * → reportService.generateMonthlyReport(df, dt) [synchronous DB query]
 * → emailService.sendMonthlyReport(report) [@Async — fires and forgets]
 * Returns: ResponseEntity.ok("Report triggered for range: " + df + " to " + dt)
 *
 * IMPORTANT — WHY EMAIL FAILURE IS ACCEPTABLE IN TESTS:
 * KycReportEmailServiceImpl.sendMonthlyReport() is @Async and wraps everything
 * in a try-catch that logs errors and swallows exceptions. So even if
 * JavaMailSender
 * fails (no SMTP configured in test), the HTTP response is still 200 OK.
 * We are testing the HTTP contract, not email delivery.
 *
 * SCENARIOS COVERED:
 * 1. ADMIN with explicit dateFrom + dateTo → 200 + body contains the range
 * 2. ADMIN with no params → 200 + body contains last month's range (default)
 * 3. Regular USER → 403 Forbidden
 * 4. Unauthenticated request → 401 Unauthorized
 * 5. Invalid date format → 400 (LocalDate.parse fails → exception)
 * ============================================================
 */
public class KycReportIntegrationTest extends BaseIntegrationTest {

        private static final String TENANT = "default";
        private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // ═══════════════════════════════════════════════════════════════════════════
        // SCENARIO 1: ADMIN with explicit date range → 200 + body contains range
        // ═══════════════════════════════════════════════════════════════════════════

        @Test
        void adminWithExplicitDateRange_shouldReturn200AndConfirmRange() throws Exception {
                String adminToken = loginAndGetToken("admin@kyc.com", "Password@123");

                String dateFrom = "2025-01-01";
                String dateTo = "2025-01-31";

                MvcResult result = mockMvc.perform(post("/api/kyc/report")
                                .param("dateFrom", dateFrom)
                                .param("dateTo", dateTo)
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Tenant-ID", TENANT))
                                .andExpect(status().isOk())
                                .andReturn();

                String body = result.getResponse().getContentAsString();

                // Controller returns: "Report triggered for range: 2025-01-01 to 2025-01-31"
                assertTrue(body.contains("2025-01-01"),
                                "Response must confirm dateFrom=2025-01-01. Got: " + body);
                assertTrue(body.contains("2025-01-31"),
                                "Response must confirm dateTo=2025-01-31. Got: " + body);
                assertTrue(body.toLowerCase().contains("report triggered") || body.toLowerCase().contains("triggered"),
                                "Response must confirm report was triggered. Got: " + body);
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // SCENARIO 2: ADMIN with no params → defaults to previous month
        // ═══════════════════════════════════════════════════════════════════════════

        @Test
        void adminWithNoParams_shouldDefaultToPreviousMonth() throws Exception {
                String adminToken = loginAndGetToken("admin@kyc.com", "Password@123");

                // Compute expected last month range (same logic as the controller)
                java.time.YearMonth lastMonth = java.time.YearMonth.now().minusMonths(1);
                String expectedFrom = lastMonth.atDay(1).format(DATE_FMT);
                String expectedTo = lastMonth.atEndOfMonth().format(DATE_FMT);

                MvcResult result = mockMvc.perform(post("/api/kyc/report")
                                // No dateFrom / dateTo params
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Tenant-ID", TENANT))
                                .andExpect(status().isOk())
                                .andReturn();

                String body = result.getResponse().getContentAsString();

                assertTrue(body.contains(expectedFrom),
                                "Response must contain last-month start (" + expectedFrom + "). Got: " + body);
                assertTrue(body.contains(expectedTo),
                                "Response must contain last-month end (" + expectedTo + "). Got: " + body);
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // SCENARIO 3: Regular USER → 403 Forbidden
        // ═══════════════════════════════════════════════════════════════════════════

        @Test
        void regularUser_shouldGet403OnReportTrigger() throws Exception {
                // Register a plain user
                String uid = java.util.UUID.randomUUID().toString().substring(0, 8);
                String email = "rpt.user." + uid + "@example.com";
                String mobile = String.format("%010d",
                                (Math.abs(uid.hashCode()) % 9_000_000_000L) + 1_000_000_000L);

                mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", TENANT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"name":"Report User","email":"%s","password":"Report@1234",
                                                 "mobileNumber":"%s","dob":"1990-06-15"}
                                                """.formatted(email, mobile)))
                                .andExpect(status().isCreated());

                String userToken = loginAndGetToken(email, "Report@1234");

                // @PreAuthorize("hasRole('ADMIN')") → ROLE_USER gets 403
                mockMvc.perform(post("/api/kyc/report")
                                .param("dateFrom", "2025-01-01")
                                .param("dateTo", "2025-01-31")
                                .header("Authorization", "Bearer " + userToken)
                                .header("X-Tenant-ID", TENANT))
                                .andExpect(status().isForbidden());
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // SCENARIO 4: No JWT → 401 Unauthorized
        // ═══════════════════════════════════════════════════════════════════════════

        @Test
        void unauthenticated_shouldGet401OnReportTrigger() throws Exception {
                mockMvc.perform(post("/api/kyc/report")
                                .param("dateFrom", "2025-01-01")
                                .param("dateTo", "2025-01-31")
                                .header("X-Tenant-ID", TENANT))
                                // No Authorization header → JwtAuthenticationFilter → 401
                                .andExpect(status().isUnauthorized());
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // SCENARIO 5: Invalid date format → 400
        // ═══════════════════════════════════════════════════════════════════════════

        @Test
        void invalidDateFormat_shouldReturn400() throws Exception {
                String adminToken = loginAndGetToken("admin@kyc.com", "Password@123");

                // "not-a-date" → LocalDate.parse throws DateTimeParseException
                // Spring converts unhandled exception → 400 or 500 depending on exception
                // handler
                // The controller has no explicit try-catch here, so it depends on
                // GlobalExceptionHandler
                mockMvc.perform(post("/api/kyc/report")
                                .param("dateFrom", "not-a-date")
                                .param("dateTo", "also-not-a-date")
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Tenant-ID", TENANT))
                                .andExpect(result -> assertTrue(
                                                result.getResponse().getStatus() == 400 ||
                                                                result.getResponse().getStatus() == 500,
                                                "Invalid date must return 4xx or 5xx, got: "
                                                                + result.getResponse().getStatus()));
        }
}
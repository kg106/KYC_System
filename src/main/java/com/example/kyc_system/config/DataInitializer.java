package com.example.kyc_system.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs on application startup (CommandLineRunner) to seed essential data:
 * Also creates a partial unique index to prevent duplicate active KYC requests.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

        private final JdbcTemplate jdbcTemplate;

        /**
         * Orchestrates the initialization of the database on application startup.
         *
         * @param args command line arguments
         * @throws Exception in case of error during initialization
         */
        @Override
        public void run(String... args) throws Exception {
                // Create a partial unique index on kyc_requests
                // Prevents a user from having multiple active KYC requests for the same
                // document type
                initUniqueIndex();
        }

        /**
         * Creates a PostgreSQL partial unique index that ensures only one active
         * (PENDING/SUBMITTED/PROCESSING) KYC request per user per document type.
         */
        private void initUniqueIndex() {
                try {
                        jdbcTemplate.execute(
                                        "CREATE UNIQUE INDEX IF NOT EXISTS unique_active_kyc " +
                                                        "ON kyc_requests(user_id, document_type) " +
                                                        "WHERE status IN ('PENDING', 'SUBMITTED', 'PROCESSING')");
                } catch (Exception e) {
                        log.warn("Could not create unique index: {}", e.getMessage());
                }
        }
}
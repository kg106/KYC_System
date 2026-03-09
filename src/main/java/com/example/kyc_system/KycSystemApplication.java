package com.example.kyc_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the KYC (Know Your Customer) System application.
 * - @EnableScheduling: Activates scheduled tasks (e.g., monthly KYC report
 * generation)
 * - @EnableAsync: Enables asynchronous method execution (e.g., audit logging,
 * email sending)
 */
@EnableScheduling
@SpringBootApplication
@EnableAsync
public class KycSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(KycSystemApplication.class, args);
	}

}

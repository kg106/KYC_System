package com.example.kyc_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableAsync
public class KycSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(KycSystemApplication.class, args);
	}

}

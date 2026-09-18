package com.saas.auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Tanpa ini, @Scheduled di ExpiredRecordCleanup tidak pernah berjalan — anotasinya
// ada tapi diam. billing-service sudah punya; auth-service belum.
@EnableScheduling
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}

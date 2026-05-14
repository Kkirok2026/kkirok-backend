package com.database2026.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DatabaseBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(DatabaseBackendApplication.class, args);
	}

}

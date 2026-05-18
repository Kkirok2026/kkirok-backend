package com.database2026.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy(
            @Value("${app.flyway.repair-before-migrate:false}") boolean repairBeforeMigrate
    ) {
        return flyway -> {
            if (repairBeforeMigrate) {
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}

package com.database2026.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.flywaydb.core.api.exception.FlywayValidateException;

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
            try {
                flyway.migrate();
            } catch (FlywayValidateException exception) {
                flyway.repair();
                flyway.migrate();
            }
        };
    }
}

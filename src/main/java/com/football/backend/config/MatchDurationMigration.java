package com.football.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchDurationMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        int updated = jdbcTemplate.update(
                "UPDATE matches SET duration = 30 WHERE duration IS NULL OR duration < 1 OR duration > 30"
        );
        if (updated > 0) {
            log.info("Длительность {} матчей приведена к лимиту 30 минут", updated);
        }
    }
}

package com.football.backend.config;

import com.football.backend.model.ParticipantStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Hibernate не всегда обновляет PostgreSQL CHECK при расширении Enum.
 * Синхронизируем допустимые статусы участников, чтобы NO_SHOW работал в production.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParticipantStatusConstraintMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) return;
        }

        List<CheckConstraint> constraints = jdbcTemplate.query("""
                SELECT c.conname, pg_get_constraintdef(c.oid) AS definition
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE t.relname = 'match_participants'
                  AND n.nspname = current_schema()
                  AND c.contype = 'c'
                  AND pg_get_constraintdef(c.oid) ILIKE '%status%'
                """, (rs, rowNum) -> new CheckConstraint(
                rs.getString("conname"), rs.getString("definition")));

        boolean alreadyCurrent = constraints.stream().anyMatch(constraint ->
                Arrays.stream(ParticipantStatus.values())
                        .allMatch(status -> constraint.definition().contains(status.name())));
        if (alreadyCurrent) return;

        constraints.forEach(constraint -> jdbcTemplate.execute(
                "ALTER TABLE match_participants DROP CONSTRAINT \""
                        + constraint.name().replace("\"", "\"\"") + "\""));

        String values = Arrays.stream(ParticipantStatus.values())
                .map(Enum::name)
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("ALTER TABLE match_participants "
                + "ADD CONSTRAINT match_participants_status_check "
                + "CHECK (status IN (" + values + "))");
        log.info("Participant status constraint synchronized with {} values",
                ParticipantStatus.values().length);
    }

    private record CheckConstraint(String name, String definition) {
    }
}

package com.football.backend.config;

import com.football.backend.model.MatchStatus;
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
 * ddl-auto=update не всегда обновляет созданный Hibernate CHECK после
 * расширения Enum. Синхронизируем ограничение без удаления данных матча.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchStatusConstraintMigration implements ApplicationRunner {
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
                WHERE t.relname = 'matches'
                  AND n.nspname = current_schema()
                  AND c.contype = 'c'
                  AND pg_get_constraintdef(c.oid) ILIKE '%status%'
                """, (rs, rowNum) -> new CheckConstraint(
                rs.getString("conname"), rs.getString("definition")));

        boolean alreadyCurrent = constraints.stream().anyMatch(constraint ->
                Arrays.stream(MatchStatus.values())
                        .allMatch(status -> constraint.definition().contains(status.name())));
        if (alreadyCurrent) return;

        constraints.forEach(constraint -> jdbcTemplate.execute(
                "ALTER TABLE matches DROP CONSTRAINT \""
                        + constraint.name().replace("\"", "\"\"") + "\""));

        String values = Arrays.stream(MatchStatus.values())
                .map(Enum::name)
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("ALTER TABLE matches ADD CONSTRAINT matches_status_check "
                + "CHECK (status IN (" + values + "))");
        log.info("Match status constraint synchronized with {} values", MatchStatus.values().length);
    }

    private record CheckConstraint(String name, String definition) {
    }
}

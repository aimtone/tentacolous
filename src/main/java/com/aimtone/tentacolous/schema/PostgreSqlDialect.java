package com.aimtone.tentacolous.schema;

import com.aimtone.tentacolous.registry.ListenerDefinition;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.stream.Collectors;

public class PostgreSqlDialect implements DatabaseDialect {

    @Override
    public boolean supports(String databaseProductName) {
        return databaseProductName != null && databaseProductName.toLowerCase().contains("postgresql");
    }

    @Override
    public void createEventInfrastructure(JdbcTemplate jdbcTemplate, String eventTable) {
        String functionName = functionName(eventTable);

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + eventTable + " ("
                + "id BIGSERIAL PRIMARY KEY, "
                + "entity_name VARCHAR(255) NOT NULL, "
                + "operation VARCHAR(20) NOT NULL, "
                + "payload TEXT NOT NULL, "
                + "status VARCHAR(20) NOT NULL DEFAULT 'PENDING', "
                + "processed BOOLEAN NOT NULL DEFAULT false, "
                + "attempts INTEGER NOT NULL DEFAULT 0, "
                + "last_error TEXT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "processing_started_at TIMESTAMP NULL, "
                + "processed_at TIMESTAMP NULL"
                + ")");

        jdbcTemplate.execute("CREATE OR REPLACE FUNCTION " + functionName + "() "
                + "RETURNS trigger AS $$ "
                + "DECLARE "
                + "payload_json jsonb; "
                + "excluded_columns text[] := TG_ARGV[1]::text[]; "
                + "BEGIN "
                + "payload_json := CASE WHEN TG_OP = 'DELETE' THEN to_jsonb(OLD) ELSE to_jsonb(NEW) END; "
                + "IF excluded_columns IS NOT NULL THEN "
                + "payload_json := payload_json - excluded_columns; "
                + "END IF; "
                + "INSERT INTO " + eventTable + "(entity_name, operation, payload) "
                + "VALUES (TG_ARGV[0], TG_OP, payload_json::text); "
                + "IF TG_OP = 'DELETE' THEN "
                + "RETURN OLD; "
                + "END IF; "
                + "RETURN NEW; "
                + "END; "
                + "$$ LANGUAGE plpgsql");
    }

    @Override
    public void validateEventInfrastructure(JdbcTemplate jdbcTemplate, String eventTable) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class,
                unqualifiedName(eventTable)
        );

        if (count == null || count == 0) {
            throw new IllegalStateException("Missing Tentacolous event table: " + eventTable);
        }
    }

    @Override
    public void createTrigger(JdbcTemplate jdbcTemplate, String eventTable, ListenerDefinition listener) {
        String triggerName = triggerName(listener.getTableName(), listener.getOperation());
        String functionName = functionName(eventTable);

        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + triggerName + " ON " + listener.getTableName());
        jdbcTemplate.execute("CREATE TRIGGER " + triggerName + " "
                + "AFTER " + listener.getOperation().name() + " ON " + listener.getTableName() + " "
                + "FOR EACH ROW "
                + "EXECUTE FUNCTION " + functionName + "('"
                + escapeLiteral(listener.getEntityName()) + "', '"
                + excludedColumnsLiteral(listener.getExcludedColumns()) + "')");
    }

    private static String functionName(String eventTable) {
        return sanitizeIdentifier(eventTable) + "_notify_change";
    }

    private static String sanitizeIdentifier(String identifier) {
        return identifier.replace(".", "_");
    }

    private static String escapeLiteral(String value) {
        return value.replace("'", "''");
    }

    private static String excludedColumnsLiteral(List<String> excludedColumns) {
        if (excludedColumns == null || excludedColumns.isEmpty()) {
            return "{}";
        }

        return excludedColumns.stream()
                .map(PostgreSqlDialect::escapeLiteral)
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String unqualifiedName(String tableName) {
        int dotIndex = tableName.lastIndexOf('.');

        if (dotIndex < 0) {
            return tableName;
        }

        return tableName.substring(dotIndex + 1);
    }
}

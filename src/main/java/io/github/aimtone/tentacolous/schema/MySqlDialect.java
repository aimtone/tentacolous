package io.github.aimtone.tentacolous.schema;

import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.registry.ListenerDefinition;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MySqlDialect implements DatabaseDialect {

    @Override
    public boolean supports(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains("mysql");
    }

    @Override
    public void createEventInfrastructure(JdbcTemplate jdbc, String table) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, entity_name VARCHAR(255) NOT NULL, "
                + "operation VARCHAR(20) NOT NULL, payload LONGTEXT NOT NULL, old_payload LONGTEXT NULL, "
                + "record_key TEXT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING', "
                + "processed BOOLEAN NOT NULL DEFAULT FALSE, attempts INT NOT NULL DEFAULT 0, "
                + "last_error LONGTEXT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "processing_started_at TIMESTAMP NULL, processed_at TIMESTAMP NULL)");
    }

    @Override
    public void validateEventInfrastructure(JdbcTemplate jdbc, String table) {
        DialectSupport.requireTable(jdbc, table);
    }

    @Override
    public void createTrigger(JdbcTemplate jdbc, String eventTable, ListenerDefinition listener) {
        List<String> columns = DialectSupport.columns(jdbc, listener.getTableName(), listener.getExcludedColumns());
        String row = listener.getOperation() == DbOperation.DELETE ? "OLD" : "NEW";
        String payload = jsonObject(columns, row);
        String oldPayload = listener.getOperation() == DbOperation.UPDATE ? jsonObject(columns, "OLD") : "NULL";
        String recordKey = row + ".`" + listener.getRecordKeyField() + "`";
        String name = triggerName(listener.getTableName(), listener.getOperation());
        jdbc.execute("DROP TRIGGER IF EXISTS " + name);
        jdbc.execute("CREATE TRIGGER " + name + " AFTER " + listener.getOperation().name() + " ON "
                + listener.getTableName() + " FOR EACH ROW INSERT INTO " + eventTable
                + " (entity_name, operation, payload, old_payload, record_key) VALUES ('"
                + DialectSupport.escapeLiteral(listener.getEntityName()) + "', '" + listener.getOperation().name()
                + "', " + payload + ", " + oldPayload + ", CAST(" + recordKey + " AS CHAR))");
    }

    protected String jsonObject(List<String> columns, String row) {
        return columns.stream().map(column -> "'" + DialectSupport.escapeLiteral(column) + "', " + row + ".`" + column + "`")
                .collect(Collectors.joining(", ", "JSON_OBJECT(", ")"));
    }

    @Override
    public String selectPendingEventsSql(String table) {
        return "SELECT id, entity_name, operation, payload, old_payload, record_key FROM " + table
                + " WHERE status = 'PENDING' ORDER BY id LIMIT ?";
    }

    @Override
    public String selectHistorySql(String table) {
        return "SELECT payload FROM " + table
                + " WHERE entity_name = ? AND id < ? AND operation IN ('INSERT', 'UPDATE') "
                + "AND (record_key = ? OR (record_key IS NULL AND JSON_UNQUOTE(JSON_EXTRACT(payload, CONCAT('$.', ?))) = ?)) ORDER BY id";
    }
}

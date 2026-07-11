package io.github.aimtone.tentacolous.schema;

import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.registry.ListenerDefinition;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class SqliteDialect implements DatabaseDialect {

    @Override
    public boolean supports(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains("sqlite");
    }

    @Override
    public void createEventInfrastructure(JdbcTemplate jdbc, String table) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "entity_name TEXT NOT NULL, operation TEXT NOT NULL, payload TEXT NOT NULL, old_payload TEXT NULL, "
                + "record_key TEXT NULL, status TEXT NOT NULL DEFAULT 'PENDING', processed INTEGER NOT NULL DEFAULT 0, "
                + "attempts INTEGER NOT NULL DEFAULT 0, last_error TEXT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "processing_started_at TEXT NULL, processed_at TEXT NULL)");
    }

    @Override
    public void validateEventInfrastructure(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                Integer.class, DialectSupport.unqualifiedName(table));
        if (count == null || count == 0) throw new IllegalStateException("Missing Tentacolous event table: " + table);
    }

    @Override
    public void createTrigger(JdbcTemplate jdbc, String eventTable, ListenerDefinition listener) {
        List<String> columns = sqliteColumns(jdbc, listener);
        DbOperation operation = listener.getOperation();
        String row = operation == DbOperation.DELETE ? "OLD" : "NEW";
        String payload = json(columns, row);
        String oldPayload = operation == DbOperation.UPDATE ? json(columns, "OLD") : "NULL";
        String name = triggerName(listener.getTableName(), operation);
        jdbc.execute("DROP TRIGGER IF EXISTS " + name);
        jdbc.execute("CREATE TRIGGER " + name + " AFTER " + operation.name() + " ON " + listener.getTableName()
                + " FOR EACH ROW BEGIN INSERT INTO " + eventTable
                + " (entity_name, operation, payload, old_payload, record_key) VALUES ('"
                + DialectSupport.escapeLiteral(listener.getEntityName()) + "', '" + operation.name() + "', "
                + payload + ", " + oldPayload + ", CAST(" + row + ".\"" + listener.getRecordKeyField()
                + "\" AS TEXT)); END");
    }

    private List<String> sqliteColumns(JdbcTemplate jdbc, ListenerDefinition listener) {
        var excluded = listener.getExcludedColumns().stream().map(String::toLowerCase).collect(Collectors.toSet());
        List<String> columns = jdbc.query("PRAGMA table_info(" + listener.getTableName() + ")",
                (rs, row) -> rs.getString("name")).stream()
                .filter(c -> !excluded.contains(c.toLowerCase(Locale.ROOT))).toList();
        if (columns.isEmpty()) throw new IllegalStateException("No payload columns found for table " + listener.getTableName());
        return columns;
    }

    private String json(List<String> columns, String row) {
        return columns.stream().map(c -> "'" + DialectSupport.escapeLiteral(c) + "', " + row + ".\"" + c + "\"")
                .collect(Collectors.joining(", ", "json_object(", ")"));
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
                + "AND (record_key = ? OR (record_key IS NULL AND json_extract(payload, '$.' || ?) = ?)) ORDER BY id";
    }

    @Override
    public String trueLiteral() { return "1"; }
}

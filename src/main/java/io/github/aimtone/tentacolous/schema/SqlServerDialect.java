package io.github.aimtone.tentacolous.schema;

import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.registry.ListenerDefinition;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class SqlServerDialect implements DatabaseDialect {

    @Override
    public boolean supports(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains("microsoft sql server");
    }

    @Override
    public void createEventInfrastructure(JdbcTemplate jdbc, String table) {
        jdbc.execute("IF OBJECT_ID(N'" + DialectSupport.escapeLiteral(table) + "', N'U') IS NULL CREATE TABLE "
                + table + " (id BIGINT IDENTITY(1,1) PRIMARY KEY, entity_name NVARCHAR(255) NOT NULL, "
                + "operation NVARCHAR(20) NOT NULL, payload NVARCHAR(MAX) NOT NULL, old_payload NVARCHAR(MAX) NULL, "
                + "record_key NVARCHAR(4000) NULL, status NVARCHAR(20) NOT NULL DEFAULT 'PENDING', "
                + "processed BIT NOT NULL DEFAULT 0, attempts INT NOT NULL DEFAULT 0, last_error NVARCHAR(MAX) NULL, "
                + "created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP, processing_started_at DATETIME2 NULL, "
                + "processed_at DATETIME2 NULL)");
    }

    @Override
    public void validateEventInfrastructure(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE name = ?", Integer.class,
                DialectSupport.unqualifiedName(table));
        if (count == null || count == 0) throw new IllegalStateException("Missing Tentacolous event table: " + table);
    }

    @Override
    public void createTrigger(JdbcTemplate jdbc, String eventTable, ListenerDefinition listener) {
        List<String> columns = DialectSupport.columns(jdbc, listener.getTableName(), listener.getExcludedColumns());
        DbOperation operation = listener.getOperation();
        String current = operation == DbOperation.DELETE ? "d" : "i";
        String payload = json(columns, current);
        String oldPayload = operation == DbOperation.UPDATE ? json(columns, "d") : "NULL";
        String from = operation == DbOperation.DELETE ? "deleted d" : "inserted i";
        if (operation == DbOperation.UPDATE) {
            from += " JOIN deleted d ON i.[" + listener.getRecordKeyField() + "] = d.["
                    + listener.getRecordKeyField() + "]";
        }
        String trigger = triggerName(listener.getTableName(), operation);
        jdbc.execute("CREATE OR ALTER TRIGGER " + trigger + " ON " + listener.getTableName() + " AFTER "
                + operation.name() + " AS BEGIN SET NOCOUNT ON; INSERT INTO " + eventTable
                + " (entity_name, operation, payload, old_payload, record_key) SELECT '"
                + DialectSupport.escapeLiteral(listener.getEntityName()) + "', '" + operation.name() + "', "
                + payload + ", " + oldPayload + ", CONVERT(NVARCHAR(4000), " + current + ".["
                + listener.getRecordKeyField() + "]) FROM " + from + "; END");
    }

    private String json(List<String> columns, String alias) {
        String select = columns.stream().map(c -> alias + ".[" + c + "] AS [" + c + "]")
                .collect(Collectors.joining(", "));
        return "(SELECT " + select + " FOR JSON PATH, WITHOUT_ARRAY_WRAPPER)";
    }

    @Override
    public String selectPendingEventsSql(String table) {
        return "SELECT TOP (?) id, entity_name, operation, payload, old_payload, record_key FROM " + table
                + " WHERE status = 'PENDING' ORDER BY id";
    }

    @Override
    public String selectHistorySql(String table) {
        return "SELECT payload FROM " + table
                + " WHERE entity_name = ? AND id < ? AND operation IN ('INSERT', 'UPDATE') "
                + "AND (record_key = ? OR (record_key IS NULL AND JSON_VALUE(payload, CONCAT('$.', ?)) = ?)) ORDER BY id";
    }

    @Override
    public String trueLiteral() { return "1"; }
}

package io.github.aimtone.tentacolous.schema;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class DialectSupport {

    private DialectSupport() {
    }

    static String unqualifiedName(String tableName) {
        int dot = tableName.lastIndexOf('.');
        return dot < 0 ? tableName : tableName.substring(dot + 1);
    }

    static String schemaName(String tableName) {
        int dot = tableName.lastIndexOf('.');
        return dot < 0 ? null : tableName.substring(0, dot);
    }

    static String escapeLiteral(String value) {
        return value.replace("'", "''");
    }

    static List<String> columns(JdbcTemplate jdbc, String tableName, List<String> excluded) {
        String schema = schemaName(tableName);
        String sql = "SELECT column_name FROM information_schema.columns WHERE table_name = ?"
                + (schema == null ? "" : " AND table_schema = ?") + " ORDER BY ordinal_position";
        Object[] args = schema == null
                ? new Object[]{unqualifiedName(tableName)}
                : new Object[]{unqualifiedName(tableName), schema};
        Set<String> ignored = excluded.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> columns = jdbc.query(sql, (rs, row) -> rs.getString(1), args).stream()
                .filter(column -> !ignored.contains(column.toLowerCase(Locale.ROOT)))
                .toList();
        if (columns.isEmpty()) {
            throw new IllegalStateException("No payload columns found for table " + tableName);
        }
        return columns;
    }

    static void requireTable(JdbcTemplate jdbc, String eventTable) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class,
                unqualifiedName(eventTable)
        );
        if (count == null || count == 0) {
            throw new IllegalStateException("Missing Tentacolous event table: " + eventTable);
        }
    }
}

package io.github.aimtone.tentacolous.schema;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

public class DatabaseDialectResolver {

    private final JdbcTemplate jdbcTemplate;
    private final List<DatabaseDialect> dialects;
    private volatile DatabaseDialect resolvedDialect;

    public DatabaseDialectResolver(JdbcTemplate jdbcTemplate, List<DatabaseDialect> dialects) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialects = List.copyOf(dialects);
    }

    public DatabaseDialectResolver(DatabaseDialect dialect) {
        this.jdbcTemplate = null;
        this.dialects = List.of(dialect);
        this.resolvedDialect = dialect;
    }

    public DatabaseDialect resolve() {
        DatabaseDialect current = resolvedDialect;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (resolvedDialect == null) {
                String productName = databaseProductName();
                resolvedDialect = dialects.stream()
                        .filter(dialect -> dialect.supports(productName))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Tentacolous does not support database '" + productName + "'. "
                                        + "Register a DatabaseDialect for this database."
                        ));
            }
            return resolvedDialect;
        }
    }

    private String databaseProductName() {
        return jdbcTemplate.execute((ConnectionCallback<String>) connection -> {
            try {
                DatabaseMetaData metadata = connection.getMetaData();
                return metadata.getDatabaseProductName();
            } catch (SQLException exception) {
                throw new IllegalStateException("Cannot read database product name", exception);
            }
        });
    }
}

package io.github.aimtone.tentacolous.schema;

import io.github.aimtone.tentacolous.config.DbListenerProperties;
import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.registry.ListenerDefinition;
import io.github.aimtone.tentacolous.registry.ListenerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class DbListenerSchemaManager {

    private static final Logger log = LoggerFactory.getLogger(DbListenerSchemaManager.class);
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_\\.]*");

    private final JdbcTemplate jdbcTemplate;
    private final ListenerRegistry listenerRegistry;
    private final DbListenerProperties properties;
    private final List<DatabaseDialect> dialects;

    private boolean initialized;

    public DbListenerSchemaManager(
            JdbcTemplate jdbcTemplate,
            ListenerRegistry listenerRegistry,
            DbListenerProperties properties
    ) {
        this(jdbcTemplate, listenerRegistry, properties, List.of(new PostgreSqlDialect()));
    }

    public DbListenerSchemaManager(
            JdbcTemplate jdbcTemplate,
            ListenerRegistry listenerRegistry,
            DbListenerProperties properties,
            List<DatabaseDialect> dialects
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.listenerRegistry = listenerRegistry;
        this.properties = properties;
        this.dialects = dialects;
    }

    public synchronized void initialize() {
        if (initialized || !properties.isEnabled()
                || properties.getSchemaManagement() == DbListenerProperties.SchemaManagement.NONE) {
            log.info("Tentacolous schema initialization skipped. initialized={}, enabled={}, schemaManagement={}",
                    initialized,
                    properties.isEnabled(),
                    properties.getSchemaManagement());
            initialized = true;
            return;
        }

        validateIdentifier(properties.getEventTable(), "event table");
        log.info("Initializing Tentacolous schema using event table '{}'", properties.getEventTable());

        DatabaseDialect dialect = resolveDialect();
        if (shouldCreateSchema()) {
            dialect.createEventInfrastructure(jdbcTemplate, properties.getEventTable());
            log.info("Tentacolous event infrastructure is ready");
        } else if (properties.getSchemaManagement() == DbListenerProperties.SchemaManagement.VALIDATE) {
            dialect.validateEventInfrastructure(jdbcTemplate, properties.getEventTable());
            log.info("Tentacolous event infrastructure validated");
        }

        Set<String> installedTriggers = new HashSet<>();

        for (ListenerDefinition listener : listenerRegistry.getAllListeners()) {
            String triggerKey = listener.getOperation() + "|" + listener.getTableName() + "|" + listener.getEntityName();

            if (!installedTriggers.add(triggerKey)) {
                continue;
            }

            if (shouldCreateSchema()) {
                createTrigger(dialect, listener);
            }
        }

        log.info("Tentacolous schema initialization completed with {} trigger(s)", installedTriggers.size());
        initialized = true;
    }

    private boolean shouldCreateSchema() {
        return properties.getSchemaManagement() == DbListenerProperties.SchemaManagement.AUTO
                || properties.getSchemaManagement() == DbListenerProperties.SchemaManagement.CREATE;
    }

    private void createTrigger(DatabaseDialect dialect, ListenerDefinition listener) {
        if (listener.getTableName() == null || listener.getTableName().isBlank()) {
            throw new IllegalStateException(
                    "@" + annotationName(listener.getOperation()) + " for " + listener.getEntityClass().getName()
                            + " must define table when tentacolous.schema-management creates database objects"
            );
        }

        validateIdentifier(listener.getTableName(), "table");
        validateExcludedColumns(listener);
        log.info("Creating Tentacolous {} trigger for table '{}' and entity '{}'",
                listener.getOperation(),
                listener.getTableName(),
                listener.getEntityName());
        dialect.createTrigger(jdbcTemplate, properties.getEventTable(), listener);
    }

    private String annotationName(DbOperation operation) {
        return switch (operation) {
            case INSERT -> "UponInserting";
            case UPDATE -> "UponUpdating";
            case DELETE -> "UponDeleting";
        };
    }

    private DatabaseDialect resolveDialect() {
        String productName = getDatabaseProductName();

        for (DatabaseDialect dialect : dialects) {
            if (dialect.supports(productName)) {
                log.info("Using Tentacolous dialect for {}", productName);
                return dialect;
            }
        }

        throw new IllegalStateException(
                "Tentacolous cannot auto-create triggers for database '" + productName
                        + "'. Set tentacolous.schema-management=none and provide your own infrastructure, "
                        + "or add a DatabaseDialect for this database."
        );
    }

    private String getDatabaseProductName() {
        return jdbcTemplate.execute((Connection connection) -> {
            try {
                DatabaseMetaData metaData = connection.getMetaData();
                return metaData.getDatabaseProductName();
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot read database product name", e);
            }
        });
    }

    private void validateIdentifier(String identifier, String name) {
        if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid " + name + " identifier: " + identifier);
        }
    }

    private void validateExcludedColumns(ListenerDefinition listener) {
        for (String excludedColumn : listener.getExcludedColumns()) {
            validateIdentifier(excludedColumn, "excluded column");
        }
    }
}

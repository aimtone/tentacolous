package io.github.aimtone.tentacolous.schema;

import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.registry.ListenerDefinition;
import org.springframework.jdbc.core.JdbcTemplate;

public interface DatabaseDialect {

    boolean supports(String databaseProductName);

    void createEventInfrastructure(JdbcTemplate jdbcTemplate, String eventTable);

    void validateEventInfrastructure(JdbcTemplate jdbcTemplate, String eventTable);

    void createTrigger(
            JdbcTemplate jdbcTemplate,
            String eventTable,
            ListenerDefinition listener
    );

    /** Builds the database-specific query used to fetch a batch of pending events. */
    String selectPendingEventsSql(String eventTable);

    /** Builds the database-specific query used to recover previous entity payloads. */
    String selectHistorySql(String eventTable);

    default Object[] selectHistoryArguments(
            String entityName, Long eventId, String recordKey, String recordKeyField
    ) {
        return new Object[]{entityName, eventId, recordKey, recordKeyField, recordKey};
    }

    default String trueLiteral() {
        return "true";
    }

    default String triggerName(String sourceTable, DbOperation operation) {
        return sourceTable.replace(".", "_") + "_tentacolous_listener_" + operation.name().toLowerCase();
    }
}

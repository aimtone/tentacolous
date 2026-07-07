package com.aimtone.tentacolous.schema;

import com.aimtone.tentacolous.model.DbOperation;
import com.aimtone.tentacolous.registry.ListenerDefinition;
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

    default String triggerName(String sourceTable, DbOperation operation) {
        return sourceTable.replace(".", "_") + "_tentacolous_listener_" + operation.name().toLowerCase();
    }
}

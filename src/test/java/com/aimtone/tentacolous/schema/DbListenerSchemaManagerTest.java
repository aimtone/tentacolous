package com.aimtone.tentacolous.schema;

import com.aimtone.tentacolous.annotations.UponDeleting;
import com.aimtone.tentacolous.annotations.UponInserting;
import com.aimtone.tentacolous.annotations.UponUpdating;
import com.aimtone.tentacolous.config.DbListenerProperties;
import com.aimtone.tentacolous.registry.ListenerRegistry;
import com.aimtone.tentacolous.scanner.DbListenerMethodScanner;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbListenerSchemaManagerTest {

    @Test
    void createsPostgreSqlInfrastructureAndTriggersForRegisteredListeners() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(invocation -> {
            ConnectionCallback<?> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        });

        ListenerRegistry registry = new ListenerRegistry();
        new DbListenerMethodScanner(registry).postProcessAfterInitialization(new ListenerBean(), "listenerBean");

        DbListenerProperties properties = new DbListenerProperties();
        DbListenerSchemaManager schemaManager = new DbListenerSchemaManager(jdbcTemplate, registry, properties);

        schemaManager.initialize();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).execute(sqlCaptor.capture());

        List<String> sql = sqlCaptor.getAllValues();

        assertThat(sql).anyMatch(statement -> statement.startsWith("CREATE TABLE IF NOT EXISTS db_change_event"));
        assertThat(sql).anyMatch(statement -> statement.contains("CREATE OR REPLACE FUNCTION db_change_event_notify_change"));
        assertThat(sql).anyMatch(statement -> statement.contains("CREATE TRIGGER user_tentacolous_listener_insert"));
        assertThat(sql).anyMatch(statement -> statement.contains("CREATE TRIGGER user_tentacolous_listener_update"));
        assertThat(sql).anyMatch(statement -> statement.contains("CREATE TRIGGER user_tentacolous_listener_delete"));
    }

    @Test
    void skipsSchemaCreationWhenDisabled() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ListenerRegistry registry = new ListenerRegistry();
        DbListenerProperties properties = new DbListenerProperties();
        properties.setEnabled(false);

        DbListenerSchemaManager schemaManager = new DbListenerSchemaManager(jdbcTemplate, registry, properties);

        schemaManager.initialize();

        org.mockito.Mockito.verify(jdbcTemplate, org.mockito.Mockito.never()).execute(anyString());
    }

    static class ListenerBean {

        @UponInserting(entity = User.class, entityName = "User")
        public void onInserting(User user) {
        }

        @UponUpdating(entity = User.class, entityName = "User")
        public void onUpdating(User user) {
        }

        @UponDeleting(entity = User.class, entityName = "User")
        public void onDeleting(User user) {
        }
    }

    static class User {
    }
}

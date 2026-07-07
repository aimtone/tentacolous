package io.github.aimtone.tentacolous.poller;

import io.github.aimtone.tentacolous.config.DbListenerProperties;
import io.github.aimtone.tentacolous.dispatcher.EventDispatcher;
import io.github.aimtone.tentacolous.model.DbChangeEvent;
import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.registry.ListenerRegistry;
import io.github.aimtone.tentacolous.schema.DbListenerSchemaManager;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbChangeEventPollerTest {

    @Test
    void pollsDispatchesAndMarksProcessedEvents() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EventDispatcher dispatcher = mock(EventDispatcher.class);
        ListenerRegistry registry = mock(ListenerRegistry.class);
        DbListenerSchemaManager schemaManager = mock(DbListenerSchemaManager.class);
        DbListenerProperties properties = new DbListenerProperties();

        DbChangeEvent event = event(10L, "User", "INSERT");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(100))).thenReturn(List.of(event));
        when(jdbcTemplate.update(
                eq("UPDATE db_change_event SET status = 'PROCESSING', attempts = attempts + 1, processing_started_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'PENDING'"),
                eq(10L)
        )).thenReturn(1);
        when(registry.hasListeners(DbOperation.INSERT, "User")).thenReturn(true);

        DbChangeEventPoller poller = new DbChangeEventPoller(
                jdbcTemplate,
                dispatcher,
                registry,
                properties,
                schemaManager,
                mock(TaskScheduler.class)
        );

        int count = poller.pollOnce();

        assertThat(count).isEqualTo(1);
        verify(dispatcher).dispatch(event, DbOperation.INSERT);
        verify(jdbcTemplate).update(
                "UPDATE db_change_event SET status = 'PROCESSED', processed = true, processed_at = CURRENT_TIMESTAMP WHERE id = ?",
                10L
        );
    }

    @Test
    void marksEventProcessedEvenWhenThereIsNoMatchingListener() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EventDispatcher dispatcher = mock(EventDispatcher.class);
        ListenerRegistry registry = mock(ListenerRegistry.class);
        DbListenerSchemaManager schemaManager = mock(DbListenerSchemaManager.class);
        DbListenerProperties properties = new DbListenerProperties();

        DbChangeEvent event = event(11L, "User", "DELETE");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(100))).thenReturn(List.of(event));
        when(jdbcTemplate.update(
                eq("UPDATE db_change_event SET status = 'PROCESSING', attempts = attempts + 1, processing_started_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'PENDING'"),
                eq(11L)
        )).thenReturn(1);
        when(registry.hasListeners(DbOperation.DELETE, "User")).thenReturn(false);

        DbChangeEventPoller poller = new DbChangeEventPoller(
                jdbcTemplate,
                dispatcher,
                registry,
                properties,
                schemaManager,
                mock(TaskScheduler.class)
        );

        poller.pollOnce();

        verify(dispatcher, never()).dispatch(any(DbChangeEvent.class), any(DbOperation.class));
        verify(jdbcTemplate).update(
                "UPDATE db_change_event SET status = 'PROCESSED', processed = true, processed_at = CURRENT_TIMESTAMP WHERE id = ?",
                11L
        );
    }

    @Test
    void startDoesNothingWhenDisabled() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DbListenerSchemaManager schemaManager = mock(DbListenerSchemaManager.class);
        DbListenerProperties properties = new DbListenerProperties();
        properties.setEnabled(false);

        DbChangeEventPoller poller = new DbChangeEventPoller(
                jdbcTemplate,
                mock(EventDispatcher.class),
                mock(ListenerRegistry.class),
                properties,
                schemaManager,
                mock(TaskScheduler.class)
        );

        poller.start();

        assertThat(poller.isRunning()).isFalse();
        verify(schemaManager, never()).initialize();
    }

    @Test
    void startRejectsInvalidConfigurationBeforeRunning() {
        DbListenerProperties properties = new DbListenerProperties();
        properties.setBatchSize(0);
        properties.setPollInterval(Duration.ZERO);

        DbChangeEventPoller poller = new DbChangeEventPoller(
                mock(JdbcTemplate.class),
                mock(EventDispatcher.class),
                mock(ListenerRegistry.class),
                properties,
                mock(DbListenerSchemaManager.class),
                mock(TaskScheduler.class)
        );

        assertThatThrownBy(poller::start)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch-size");

        assertThat(poller.isRunning()).isFalse();
    }

    private DbChangeEvent event(Long id, String entityName, String operation) {
        DbChangeEvent event = new DbChangeEvent();
        event.setId(id);
        event.setEntityName(entityName);
        event.setOperation(operation);
        event.setPayload("{\"id\":" + id + "}");
        return event;
    }
}

package com.aimtone.tentacolous.poller;

import com.aimtone.tentacolous.config.DbListenerProperties;
import com.aimtone.tentacolous.dispatcher.EventDispatcher;
import com.aimtone.tentacolous.model.DbChangeEvent;
import com.aimtone.tentacolous.model.DbOperation;
import com.aimtone.tentacolous.registry.ListenerRegistry;
import com.aimtone.tentacolous.schema.DbListenerSchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class DbChangeEventPoller implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DbChangeEventPoller.class);
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_\\.]*");

    private final JdbcTemplate jdbcTemplate;
    private final EventDispatcher eventDispatcher;
    private final ListenerRegistry listenerRegistry;
    private final DbListenerProperties properties;
    private final DbListenerSchemaManager schemaManager;
    private final TaskScheduler taskScheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledFuture<?> scheduledTask;

    public DbChangeEventPoller(
            JdbcTemplate jdbcTemplate,
            EventDispatcher eventDispatcher,
            ListenerRegistry listenerRegistry,
            DbListenerProperties properties,
            DbListenerSchemaManager schemaManager,
            TaskScheduler taskScheduler
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventDispatcher = eventDispatcher;
        this.listenerRegistry = listenerRegistry;
        this.properties = properties;
        this.schemaManager = schemaManager;
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void start() {
        if (!properties.isEnabled()) {
            return;
        }

        validateProperties();

        if (!running.compareAndSet(false, true)) {
            return;
        }

        schemaManager.initialize();
        log.info("Starting Tentacolous poller with interval={}, initialDelay={}, batchSize={}",
                properties.getPollInterval(),
                properties.getInitialDelay(),
                properties.getBatchSize());

        scheduledTask = taskScheduler.scheduleWithFixedDelay(
                this::safePollOnce,
                Instant.now().plus(properties.getInitialDelay()),
                properties.getPollInterval()
        );
    }

    @Override
    public void stop() {
        running.set(false);

        if (scheduledTask != null) {
            scheduledTask.cancel(true);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void safePollOnce() {
        if (!running.get()) {
            return;
        }

        try {
            pollOnce();
        } catch (Exception e) {
            log.error("Error polling database change events", e);
        }
    }

    public int pollOnce() {
        List<DbChangeEvent> events = jdbcTemplate.query(selectSql(), this::mapEvent, properties.getBatchSize());

        for (DbChangeEvent event : events) {
            processEvent(event);
        }

        return events.size();
    }

    private void processEvent(DbChangeEvent event) {
        try {
            if (!claimEvent(event.getId())) {
                return;
            }

            DbOperation operation = DbOperation.valueOf(event.getOperation());

            if (listenerRegistry.hasListeners(operation, event.getEntityName())) {
                eventDispatcher.dispatch(event, operation);
            }

            markProcessed(event.getId());
        } catch (Exception e) {
            log.error("Error processing database change event id={}", event.getId(), e);
            markFailed(event.getId(), e);
        }
    }

    private DbChangeEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        DbChangeEvent event = new DbChangeEvent();
        event.setId(rs.getLong("id"));
        event.setEntityName(rs.getString("entity_name"));
        event.setOperation(rs.getString("operation"));
        event.setPayload(rs.getString("payload"));
        return event;
    }

    private String selectSql() {
        return "SELECT id, entity_name, operation, payload FROM " + properties.getEventTable()
                + " WHERE status = 'PENDING' ORDER BY id LIMIT ?";
    }

    private boolean claimEvent(Long eventId) {
        int updatedRows = jdbcTemplate.update(
                "UPDATE " + properties.getEventTable()
                        + " SET status = 'PROCESSING', attempts = attempts + 1, processing_started_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND status = 'PENDING'",
                eventId
        );

        return updatedRows == 1;
    }

    private void markProcessed(Long eventId) {
        jdbcTemplate.update(
                "UPDATE " + properties.getEventTable()
                        + " SET status = 'PROCESSED', processed = true, processed_at = CURRENT_TIMESTAMP WHERE id = ?",
                eventId
        );
    }

    private void markFailed(Long eventId, Exception exception) {
        if (eventId == null) {
            return;
        }

        jdbcTemplate.update(
                "UPDATE " + properties.getEventTable()
                        + " SET status = CASE WHEN attempts >= ? THEN 'FAILED' ELSE 'PENDING' END, "
                        + "last_error = ? WHERE id = ?",
                properties.getMaxAttempts(),
                rootMessage(exception),
                eventId
        );
    }

    private void validateProperties() {
        if (properties.getBatchSize() <= 0) {
            throw new IllegalArgumentException("tentacolous.batch-size must be greater than zero");
        }

        if (properties.getPollInterval() == null || properties.getPollInterval().isNegative()) {
            throw new IllegalArgumentException("tentacolous.poll-interval must be zero or greater");
        }

        if (properties.getInitialDelay() == null || properties.getInitialDelay().isNegative()) {
            throw new IllegalArgumentException("tentacolous.initial-delay must be zero or greater");
        }

        if (properties.getMaxAttempts() <= 0) {
            throw new IllegalArgumentException("tentacolous.max-attempts must be greater than zero");
        }

        if (!SAFE_TABLE_NAME.matcher(properties.getEventTable()).matches()) {
            throw new IllegalArgumentException("Invalid tentacolous.event-table: " + properties.getEventTable());
        }
    }

    private String rootMessage(Exception exception) {
        Throwable current = exception;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current.getMessage();
    }
}

package io.github.aimtone.tentacolous.sink;

import io.github.aimtone.tentacolous.model.DbChangeEvent;
import io.github.aimtone.tentacolous.model.DbOperation;

import java.time.Instant;

/**
 * Immutable view of a database change handed to every {@link ChangeEventSink}.
 *
 * <p>The payloads are kept as raw JSON strings exactly as the database trigger produced them,
 * so a sink can forward them without an intermediate object mapping.
 */
public final class ChangeEvent {

    private final long eventId;
    private final String entityName;
    private final DbOperation operation;
    private final String recordKey;
    private final String payload;
    private final String oldPayload;
    private final Instant observedAt;

    public ChangeEvent(
            long eventId,
            String entityName,
            DbOperation operation,
            String recordKey,
            String payload,
            String oldPayload,
            Instant observedAt
    ) {
        this.eventId = eventId;
        this.entityName = entityName;
        this.operation = operation;
        this.recordKey = recordKey;
        this.payload = payload;
        this.oldPayload = oldPayload;
        this.observedAt = observedAt;
    }

    public static ChangeEvent from(DbChangeEvent event, DbOperation operation) {
        return new ChangeEvent(
                event.getId(),
                event.getEntityName(),
                operation,
                event.getRecordKey(),
                event.getPayload(),
                event.getOldPayload(),
                Instant.now()
        );
    }

    /** Unique, monotonically increasing id of the row in the Tentacolous event table. Use it as a deduplication key. */
    public long getEventId() {
        return eventId;
    }

    public String getEntityName() {
        return entityName;
    }

    public DbOperation getOperation() {
        return operation;
    }

    /** Value of the record primary key, as text. May be {@code null} when the trigger could not resolve it. */
    public String getRecordKey() {
        return recordKey;
    }

    /**
     * Current row as JSON for {@code INSERT} and {@code UPDATE}; the removed row as JSON for {@code DELETE}.
     */
    public String getPayload() {
        return payload;
    }

    /** Previous row as JSON. Only present for {@code UPDATE}; {@code null} otherwise. */
    public String getOldPayload() {
        return oldPayload;
    }

    /** Instant the poller read this change from the event table. */
    public Instant getObservedAt() {
        return observedAt;
    }
}

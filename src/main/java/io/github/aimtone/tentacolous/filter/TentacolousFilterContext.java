package io.github.aimtone.tentacolous.filter;

import io.github.aimtone.tentacolous.model.DbOperation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class TentacolousFilterContext<T> {

    private final T entity;
    private final T oldEntity;
    private final DbOperation operation;
    private final Long eventId;
    private final String entityName;
    private final String recordKey;
    private final Set<String> changedFields;

    public TentacolousFilterContext(T entity, T oldEntity, DbOperation operation) {
        this(entity, oldEntity, operation, null, null, null, Collections.emptySet());
    }

    public TentacolousFilterContext(
            T entity,
            T oldEntity,
            DbOperation operation,
            Long eventId,
            String entityName,
            String recordKey
    ) {
        this(entity, oldEntity, operation, eventId, entityName, recordKey, Collections.emptySet());
    }

    public TentacolousFilterContext(
            T entity,
            T oldEntity,
            DbOperation operation,
            Long eventId,
            String entityName,
            String recordKey,
            Set<String> changedFields
    ) {
        this.entity = entity;
        this.oldEntity = oldEntity;
        this.operation = operation;
        this.eventId = eventId;
        this.entityName = entityName;
        this.recordKey = recordKey;
        this.changedFields = changedFields == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(changedFields));
    }

    public T getEntity() {
        return entity;
    }

    public T getOldEntity() {
        return oldEntity;
    }

    public DbOperation getOperation() {
        return operation;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getRecordKey() {
        return recordKey;
    }

    public Set<String> getChangedFields() {
        return changedFields;
    }

    /**
     * Returns whether the named JSON payload field changed during an update.
     * Always returns false for insert and delete events.
     */
    public boolean hasChanged(String fieldName) {
        return fieldName != null && changedFields.contains(fieldName);
    }
}

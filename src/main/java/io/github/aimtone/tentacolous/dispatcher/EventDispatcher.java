package io.github.aimtone.tentacolous.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aimtone.tentacolous.config.DbListenerProperties;
import io.github.aimtone.tentacolous.model.DbChangeEvent;
import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.filter.TentacolousFilter;
import io.github.aimtone.tentacolous.filter.TentacolousFilterContext;
import io.github.aimtone.tentacolous.registry.ListenerDefinition;
import io.github.aimtone.tentacolous.registry.ListenerRegistry;
import io.github.aimtone.tentacolous.schema.DatabaseDialectResolver;
import io.github.aimtone.tentacolous.schema.PostgreSqlDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_\\.]*");

    private record ResolvedEntities(Object entity, Object oldEntity) {
    }

    private final ListenerRegistry listenerRegistry;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DbListenerProperties properties;
    private final DatabaseDialectResolver dialectResolver;

    public EventDispatcher(ListenerRegistry listenerRegistry, ObjectMapper objectMapper) {
        this(listenerRegistry, objectMapper, null, null, null);
    }

    public EventDispatcher(
            ListenerRegistry listenerRegistry,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            DbListenerProperties properties
    ) {
        this(listenerRegistry, objectMapper, jdbcTemplate, properties,
                jdbcTemplate == null ? null : new DatabaseDialectResolver(new PostgreSqlDialect()));
    }

    public EventDispatcher(
            ListenerRegistry listenerRegistry,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            DbListenerProperties properties,
            DatabaseDialectResolver dialectResolver
    ) {
        this.listenerRegistry = listenerRegistry;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.dialectResolver = dialectResolver;
    }

    public void dispatchInsert(DbChangeEvent event, Class<?> entityClass) {
        List<ListenerDefinition> listeners = listenerRegistry.getInsertListeners(entityClass);
        dispatch(event, listeners);
    }

    public void dispatchInsert(DbChangeEvent event) {
        dispatch(event, DbOperation.INSERT);
    }

    public void dispatch(DbChangeEvent event) {
        dispatch(event, DbOperation.valueOf(event.getOperation()));
    }

    public void dispatch(DbChangeEvent event, DbOperation operation) {
        List<ListenerDefinition> listeners = listenerRegistry.getListeners(operation, event.getEntityName());
        dispatch(event, listeners);
    }

    private void dispatch(DbChangeEvent event, List<ListenerDefinition> listeners) {
        JsonNode payload = null;

        for (ListenerDefinition listener : listeners) {
            if (listener.hasCustomFilter()) {
                ResolvedEntities entities = readEntities(listener, event, true);
                if (matchesCustomFilter(listener, event, entities)) {
                    invokeListener(listener, event, entities);
                } else {
                    log.debug(
                            "Custom filter {} rejected {} event {} for entity {} and listener {}.{}",
                            listener.getCustomFilter().getClass().getName(),
                            listener.getOperation(),
                            event.getId(),
                            listener.getEntityName(),
                            listener.getBean().getClass().getName(),
                            listener.getMethod().getName()
                    );
                }
                continue;
            }

            if (listener.getFilter().isEnabled() && payload == null) {
                payload = readPayload(event);
            }

            if (!listener.getFilter().isEnabled() || listener.getFilter().matches(payload)) {
                invokeListener(listener, event, null);
            }
        }
    }

    private JsonNode readPayload(DbChangeEvent event) {
        try {
            return objectMapper.readTree(event.getPayload());
        } catch (Exception e) {
            throw new RuntimeException("Error reading database change event payload", e);
        }
    }

    private void invokeListener(
            ListenerDefinition listener,
            DbChangeEvent event,
            ResolvedEntities resolvedEntities
    ) {
        try {
            Method method = listener.getMethod();
            int parameterCount = method.getParameterCount();
            ResolvedEntities entities = resolvedEntities != null
                    ? resolvedEntities
                    : readEntities(listener, event, parameterCount >= 2);
            Object entity = entities.entity();
            Object oldEntity = entities.oldEntity();

            if (listener.getOperation() == DbOperation.UPDATE && parameterCount == 3) {
                Object history = readHistory(event, listener, method.getParameterTypes()[2]);
                method.invoke(listener.getBean(), entity, oldEntity, history);
            } else if (listener.getOperation() == DbOperation.UPDATE && parameterCount == 2) {
                method.invoke(listener.getBean(), entity, oldEntity);
            } else if (listener.getOperation() == DbOperation.DELETE && parameterCount == 2) {
                Object history = readHistory(event, listener, method.getParameterTypes()[1]);
                method.invoke(listener.getBean(), entity, history);
            } else {
                method.invoke(listener.getBean(), entity);
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Error executing " + listener.getOperation()
                            + " listener " + listener.getBean().getClass().getName()
                            + "." + listener.getMethod().getName()
                            + " for entity " + listener.getEntityName()
                            + " and event " + event.getId(),
                    e
            );
        }
    }

    private ResolvedEntities readEntities(
            ListenerDefinition listener,
            DbChangeEvent event,
            boolean includeOldEntity
    ) {
        try {
            Object entity = objectMapper.readValue(event.getPayload(), listener.getEntityClass());
            Object oldEntity = null;

            if (includeOldEntity && listener.getOperation() == DbOperation.UPDATE && event.getOldPayload() != null) {
                oldEntity = objectMapper.readValue(event.getOldPayload(), listener.getEntityClass());
            }

            return new ResolvedEntities(entity, oldEntity);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error reading payload for " + listener.getOperation()
                            + " listener " + listener.getMethod().getName()
                            + " and entity " + listener.getEntityName(),
                    e
            );
        }
    }

    private boolean matchesCustomFilter(
            ListenerDefinition listener,
            DbChangeEvent event,
            ResolvedEntities entities
    ) {
        try {
            return accept(listener.getCustomFilter(), entities, listener.getOperation(), event);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error executing filter " + listener.getCustomFilter().getClass().getName()
                            + " for " + listener.getOperation()
                            + " listener " + listener.getBean().getClass().getName()
                            + "." + listener.getMethod().getName()
                            + " and entity " + listener.getEntityName()
                            + " on event " + event.getId(),
                    e
            );
        }
    }

    @SuppressWarnings("unchecked")
    private <T> boolean accept(
            TentacolousFilter<?> filter,
            ResolvedEntities entities,
            DbOperation operation,
            DbChangeEvent event
    ) {
        TentacolousFilter<T> typedFilter = (TentacolousFilter<T>) filter;
        return typedFilter.accept(new TentacolousFilterContext<>(
                (T) entities.entity(),
                (T) entities.oldEntity(),
                operation,
                event.getId(),
                event.getEntityName(),
                event.getRecordKey(),
                changedFields(event, operation)
        ));
    }

    private Set<String> changedFields(DbChangeEvent event, DbOperation operation) {
        if (operation != DbOperation.UPDATE || event.getOldPayload() == null) {
            return Collections.emptySet();
        }

        try {
            JsonNode currentPayload = objectMapper.readTree(event.getPayload());
            JsonNode oldPayload = objectMapper.readTree(event.getOldPayload());
            Set<String> fieldNames = new LinkedHashSet<>();
            currentPayload.fieldNames().forEachRemaining(fieldNames::add);
            oldPayload.fieldNames().forEachRemaining(fieldNames::add);
            fieldNames.removeIf(fieldName -> Objects.equals(
                    currentPayload.get(fieldName),
                    oldPayload.get(fieldName)
            ));
            return fieldNames;
        } catch (Exception e) {
            throw new RuntimeException("Error comparing current and previous event payloads", e);
        }
    }

    private Object readHistory(DbChangeEvent event, ListenerDefinition listener, Class<?> historyParameterType) {
        List<Object> history = readHistoryEntities(event, listener);

        if (historyParameterType.isArray()) {
            Object array = Array.newInstance(historyParameterType.getComponentType(), history.size());

            for (int i = 0; i < history.size(); i++) {
                Array.set(array, i, history.get(i));
            }

            return array;
        }

        return history;
    }

    private List<Object> readHistoryEntities(DbChangeEvent event, ListenerDefinition listener) {
        if (jdbcTemplate == null || properties == null || dialectResolver == null) {
            return Collections.emptyList();
        }

        if (event.getId() == null) {
            return Collections.emptyList();
        }

        String recordKey = resolveRecordKey(event, listener);

        if (recordKey == null || recordKey.isBlank()) {
            return Collections.emptyList();
        }

        String eventTable = properties.getEventTable();

        if (!SAFE_TABLE_NAME.matcher(eventTable).matches()) {
            throw new IllegalArgumentException("Invalid tentacolous.event-table: " + eventTable);
        }

        var dialect = dialectResolver.resolve();
        List<String> payloads = jdbcTemplate.query(
                dialect.selectHistorySql(eventTable),
                (rs, rowNum) -> rs.getString("payload"),
                dialect.selectHistoryArguments(event.getEntityName(), event.getId(), recordKey,
                        listener.getRecordKeyField())
        );

        List<Object> history = new ArrayList<>(payloads.size());

        for (String payload : payloads) {
            try {
                history.add(objectMapper.readValue(payload, listener.getEntityClass()));
            } catch (Exception e) {
                throw new RuntimeException("Error reading database change event history payload", e);
            }
        }

        return history;
    }

    private String resolveRecordKey(DbChangeEvent event, ListenerDefinition listener) {
        if (event.getRecordKey() != null && !event.getRecordKey().isBlank()) {
            return event.getRecordKey();
        }

        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            JsonNode keyNode = payload.get(listener.getRecordKeyField());

            if (keyNode == null || keyNode.isNull()) {
                return null;
            }

            return keyNode.asText();
        } catch (Exception e) {
            throw new RuntimeException("Error reading database change event record key", e);
        }
    }
}

package io.github.aimtone.tentacolous.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aimtone.tentacolous.model.DbChangeEvent;
import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.registry.ListenerDefinition;
import io.github.aimtone.tentacolous.registry.ListenerRegistry;

import java.util.List;

public class EventDispatcher {

    private final ListenerRegistry listenerRegistry;
    private final ObjectMapper objectMapper;

    public EventDispatcher(ListenerRegistry listenerRegistry, ObjectMapper objectMapper) {
        this.listenerRegistry = listenerRegistry;
        this.objectMapper = objectMapper;
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
            if (listener.getFilter().isEnabled() && payload == null) {
                payload = readPayload(event);
            }

            if (!listener.getFilter().isEnabled() || listener.getFilter().matches(payload)) {
                invokeListener(listener, event);
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

    private void invokeListener(ListenerDefinition listener, DbChangeEvent event) {
        try {
            Object entity = objectMapper.readValue(
                    event.getPayload(),
                    listener.getEntityClass()
            );

            listener.getMethod().invoke(listener.getBean(), entity);

        } catch (Exception e) {
            throw new RuntimeException("Error executing @UponInserting listener", e);
        }
    }
}

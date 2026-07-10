package io.github.aimtone.tentacolous.registry;

import io.github.aimtone.tentacolous.model.DbOperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ListenerRegistry {

    private static final Comparator<ListenerDefinition> LISTENER_ORDER =
            Comparator.comparingInt(ListenerDefinition::getOrder);

    private final Map<DbOperation, Map<Class<?>, List<ListenerDefinition>>> listeners = new ConcurrentHashMap<>();
    private final Map<DbOperation, Map<String, List<ListenerDefinition>>> listenersByName = new ConcurrentHashMap<>();

    public void registerInsertListener(Class<?> entityClass, String entityName, ListenerMethod listenerMethod) {
        registerListener(DbOperation.INSERT, entityClass, entityName, toDefinition(listenerMethod));
    }

    public void registerUpdateListener(Class<?> entityClass, String entityName, ListenerMethod listenerMethod) {
        registerListener(DbOperation.UPDATE, entityClass, entityName, toDefinition(listenerMethod));
    }

    public void registerDeleteListener(Class<?> entityClass, String entityName, ListenerMethod listenerMethod) {
        registerListener(DbOperation.DELETE, entityClass, entityName, toDefinition(listenerMethod));
    }

    public void registerListener(ListenerDefinition listener) {
        registerListener(listener.getOperation(), listener.getEntityClass(), listener.getEntityName(), listener);
    }

    public void registerListener(
            DbOperation operation,
            Class<?> entityClass,
            String entityName,
            ListenerDefinition listener
    ) {
        listeners
                .computeIfAbsent(operation, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(entityClass, key -> new ArrayList<>())
                .add(listener);
        listeners.get(operation).get(entityClass).sort(LISTENER_ORDER);

        registerEntityName(operation, entityClass.getName(), listener);
        registerEntityName(operation, entityClass.getSimpleName(), listener);

        if (entityName != null && !entityName.isBlank()) {
            registerEntityName(operation, entityName, listener);
        }
    }

    public List<ListenerDefinition> getInsertListeners(Class<?> entityClass) {
        return getListeners(DbOperation.INSERT, entityClass);
    }

    public List<ListenerDefinition> getInsertListeners(String entityName) {
        return getListeners(DbOperation.INSERT, entityName);
    }

    public List<ListenerDefinition> getUpdateListeners(String entityName) {
        return getListeners(DbOperation.UPDATE, entityName);
    }

    public List<ListenerDefinition> getDeleteListeners(String entityName) {
        return getListeners(DbOperation.DELETE, entityName);
    }

    public List<ListenerDefinition> getListeners(DbOperation operation, Class<?> entityClass) {
        return listeners
                .getOrDefault(operation, Collections.emptyMap())
                .getOrDefault(entityClass, Collections.emptyList());
    }

    public List<ListenerDefinition> getListeners(DbOperation operation, String entityName) {
        if (entityName == null || entityName.isBlank()) {
            return Collections.emptyList();
        }

        return listenersByName
                .getOrDefault(operation, Collections.emptyMap())
                .getOrDefault(entityName, Collections.emptyList());
    }

    public boolean hasInsertListeners(Class<?> entityClass) {
        return hasListeners(DbOperation.INSERT, entityClass);
    }

    public boolean hasInsertListeners(String entityName) {
        return hasListeners(DbOperation.INSERT, entityName);
    }

    public boolean hasListeners(DbOperation operation, String entityName) {
        return listenersByName
                .getOrDefault(operation, Collections.emptyMap())
                .containsKey(entityName);
    }

    public boolean hasListeners(DbOperation operation, Class<?> entityClass) {
        return listeners
                .getOrDefault(operation, Collections.emptyMap())
                .containsKey(entityClass);
    }

    public List<ListenerDefinition> getAllInsertListeners() {
        return getAllListeners(DbOperation.INSERT);
    }

    public List<ListenerDefinition> getAllListeners() {
        Set<ListenerDefinition> uniqueListeners = new HashSet<>();

        for (Map<Class<?>, List<ListenerDefinition>> operationListeners : listeners.values()) {
            for (List<ListenerDefinition> listenerMethods : operationListeners.values()) {
                uniqueListeners.addAll(listenerMethods);
            }
        }

        return new ArrayList<>(uniqueListeners);
    }

    public List<ListenerDefinition> getAllListeners(DbOperation operation) {
        Set<ListenerDefinition> uniqueListeners = new HashSet<>();

        for (List<ListenerDefinition> listenerMethods : listeners
                .getOrDefault(operation, Collections.emptyMap())
                .values()) {
            uniqueListeners.addAll(listenerMethods);
        }

        return new ArrayList<>(uniqueListeners);
    }

    private void registerEntityName(DbOperation operation, String entityName, ListenerDefinition listener) {
        List<ListenerDefinition> operationListeners = listenersByName
                .computeIfAbsent(operation, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(entityName, key -> new ArrayList<>());

        if (!operationListeners.contains(listener)) {
            operationListeners.add(listener);
            operationListeners.sort(LISTENER_ORDER);
        }
    }

    private ListenerDefinition toDefinition(ListenerMethod listenerMethod) {
        return new ListenerDefinition(
                listenerMethod.getBean(),
                listenerMethod.getMethod(),
                listenerMethod.getOperation(),
                listenerMethod.getEntityClass(),
                listenerMethod.getEntityName(),
                listenerMethod.getTableName(),
                "id",
                new ListenerFilter(
                        listenerMethod.getFieldName(),
                        listenerMethod.getValueType(),
                        listenerMethod.getStringValue()
                ),
                new String[0]
        );
    }
}

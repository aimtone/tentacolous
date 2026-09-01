package io.github.aimtone.tentacolous.registry;

import io.github.aimtone.tentacolous.model.DbOperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds listener-less capture declarations (from {@code @TentacolousCapture} or {@code Capture}
 * beans). These do not invoke any Java method; they make the schema manager create the database
 * trigger and let the poller decide, through their filters, which changes reach the configured
 * sinks.
 */
public class CaptureRegistry {

    private final List<ListenerDefinition> definitions = new ArrayList<>();

    public void register(ListenerDefinition definition) {
        definitions.add(definition);
    }

    public List<ListenerDefinition> getAll() {
        return Collections.unmodifiableList(definitions);
    }

    /** Captures declared for the given operation and event entity name. */
    public List<ListenerDefinition> getMatching(DbOperation operation, String entityName) {
        List<ListenerDefinition> matching = new ArrayList<>();

        for (ListenerDefinition definition : definitions) {
            if (definition.getOperation() != operation) {
                continue;
            }

            if (matchesEntity(definition, entityName)) {
                matching.add(definition);
            }
        }

        return matching;
    }

    public boolean isEmpty() {
        return definitions.isEmpty();
    }

    private boolean matchesEntity(ListenerDefinition definition, String entityName) {
        if (entityName == null) {
            return false;
        }

        if (entityName.equals(definition.getEntityName())) {
            return true;
        }

        Class<?> entityClass = definition.getEntityClass();
        return entityClass != null
                && (entityName.equals(entityClass.getSimpleName()) || entityName.equals(entityClass.getName()));
    }
}

package io.github.aimtone.tentacolous.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aimtone.tentacolous.filter.PayloadChanges;
import io.github.aimtone.tentacolous.filter.TentacolousFilter;
import io.github.aimtone.tentacolous.filter.TentacolousFilterContext;
import io.github.aimtone.tentacolous.model.DbChangeEvent;
import io.github.aimtone.tentacolous.model.DbOperation;
import io.github.aimtone.tentacolous.registry.CaptureRegistry;
import io.github.aimtone.tentacolous.registry.ListenerDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Decides whether a change event may be forwarded to the sinks, based on the {@code Capture}
 * declarations for its entity.
 *
 * <ul>
 *   <li>No capture declared for the entity and operation: the event is forwarded (a listener-only
 *       entity keeps the default "forward everything" behavior).</li>
 *   <li>One or more captures declared: the event is forwarded when at least one of them accepts it
 *       (declarative or programmatic filter).</li>
 * </ul>
 */
public class CapturePublicationFilter {

    private static final Logger log = LoggerFactory.getLogger(CapturePublicationFilter.class);

    private final CaptureRegistry captureRegistry;
    private final ObjectMapper objectMapper;

    public CapturePublicationFilter(CaptureRegistry captureRegistry, ObjectMapper objectMapper) {
        this.captureRegistry = captureRegistry;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public boolean allows(DbChangeEvent event, DbOperation operation) {
        if (captureRegistry == null) {
            return true;
        }

        List<ListenerDefinition> captures = captureRegistry.getMatching(operation, event.getEntityName());

        if (captures.isEmpty()) {
            return true;
        }

        for (ListenerDefinition capture : captures) {
            if (accepts(capture, event, operation)) {
                return true;
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Capture filters rejected {} event {} for entity {}; not forwarding to sinks",
                    operation, event.getId(), event.getEntityName());
        }

        return false;
    }

    private boolean accepts(ListenerDefinition capture, DbChangeEvent event, DbOperation operation) {
        if (capture.hasCustomFilter()) {
            return acceptsCustom(capture, event, operation);
        }

        if (!capture.getFilter().isEnabled()) {
            return true;
        }

        try {
            return capture.getFilter().matches(objectMapper.readTree(event.getPayload()));
        } catch (Exception e) {
            throw new RuntimeException("Error evaluating capture filter for entity " + capture.getEntityName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean acceptsCustom(ListenerDefinition capture, DbChangeEvent event, DbOperation operation) {
        try {
            Object entity = objectMapper.readValue(event.getPayload(), capture.getEntityClass());
            Object oldEntity = null;

            if (operation == DbOperation.UPDATE && event.getOldPayload() != null) {
                oldEntity = objectMapper.readValue(event.getOldPayload(), capture.getEntityClass());
            }

            TentacolousFilter<Object> filter = (TentacolousFilter<Object>) capture.getCustomFilter();
            return filter.accept(new TentacolousFilterContext<>(
                    entity,
                    oldEntity,
                    operation,
                    event.getId(),
                    event.getEntityName(),
                    event.getRecordKey(),
                    operation == DbOperation.UPDATE
                            ? PayloadChanges.changedFields(objectMapper, event.getPayload(), event.getOldPayload())
                            : java.util.Collections.emptySet()
            ));
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error evaluating capture filter " + capture.getCustomFilter().getClass().getName()
                            + " for entity " + capture.getEntityName(), e);
        }
    }
}

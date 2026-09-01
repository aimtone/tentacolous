package io.github.aimtone.tentacolous.capture;

import io.github.aimtone.tentacolous.annotations.ValueType;
import io.github.aimtone.tentacolous.filter.TentacolousFilter;
import io.github.aimtone.tentacolous.model.DbOperation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Programmatic declaration that an entity's table must be captured and forwarded to the configured
 * {@link io.github.aimtone.tentacolous.sink.ChangeEventSink sinks}. Expose it as a Spring bean:
 *
 * <pre>
 * &#64;Configuration
 * public class TentacolousConfig {
 *
 *     &#64;Bean
 *     Capture personCapture() {
 *         return Capture.of(Person.class);
 *     }
 *
 *     &#64;Bean
 *     Capture approvedOrderCapture() {
 *         return Capture.of(Order.class)
 *                 .operations(DbOperation.INSERT, DbOperation.UPDATE)
 *                 .where("status", ValueType.STRING, "APPROVED")
 *                 .exclude("internal_notes");
 *     }
 *
 *     &#64;Bean
 *     Capture activePersonCapture(ActivePersonFilter filter) {
 *         return Capture.of(Person.class).filter(filter);
 *     }
 * }
 * </pre>
 *
 * It is the code equivalent of {@link io.github.aimtone.tentacolous.annotations.TentacolousCapture}.
 * A {@code Capture} never invokes a Java method; its filters decide which changes reach the sinks.
 */
public final class Capture {

    private final Class<?> entity;
    private final Set<DbOperation> operations = EnumSet.noneOf(DbOperation.class);
    private final List<String> excludedColumns = new ArrayList<>();
    private String entityName;
    private String field;
    private ValueType valueType = ValueType.NONE;
    private String value;
    private TentacolousFilter<?> filter;
    private int order;

    private Capture(Class<?> entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Capture entity must not be null");
        }
        this.entity = entity;
    }

    public static Capture of(Class<?> entity) {
        return new Capture(entity);
    }

    /** Restricts the captured operations. When never called, all of INSERT, UPDATE and DELETE are captured. */
    public Capture operations(DbOperation... operations) {
        this.operations.addAll(Arrays.asList(operations));
        return this;
    }

    public Capture operation(DbOperation operation) {
        this.operations.add(operation);
        return this;
    }

    /** Overrides the logical event name. Defaults to the entity class simple name. */
    public Capture entityName(String entityName) {
        this.entityName = entityName;
        return this;
    }

    /** Columns removed from the stored and forwarded payload. */
    public Capture exclude(String... columns) {
        this.excludedColumns.addAll(Arrays.asList(columns));
        return this;
    }

    /**
     * Declarative filter: only changes whose {@code field} equals {@code value} (interpreted as
     * {@code valueType}) are forwarded to the sinks.
     */
    public Capture where(String field, ValueType valueType, String value) {
        this.field = field;
        this.valueType = valueType;
        this.value = value;
        return this;
    }

    /**
     * Programmatic filter: only changes accepted by the given {@link TentacolousFilter} are forwarded.
     * Takes precedence over {@link #where(String, ValueType, String)} when both are set.
     */
    public Capture filter(TentacolousFilter<?> filter) {
        this.filter = filter;
        return this;
    }

    /** Ordering hint relative to other captures and listeners. Lower runs first. */
    public Capture order(int order) {
        this.order = order;
        return this;
    }

    public Class<?> getEntity() {
        return entity;
    }

    public Set<DbOperation> getOperations() {
        return operations.isEmpty() ? EnumSet.allOf(DbOperation.class) : EnumSet.copyOf(operations);
    }

    public List<String> getExcludedColumns() {
        return Collections.unmodifiableList(excludedColumns);
    }

    public String getEntityName() {
        return entityName;
    }

    public String getField() {
        return field;
    }

    public ValueType getValueType() {
        return valueType;
    }

    public String getValue() {
        return value;
    }

    public TentacolousFilter<?> getFilter() {
        return filter;
    }

    public int getOrder() {
        return order;
    }
}

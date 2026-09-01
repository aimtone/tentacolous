package io.github.aimtone.tentacolous.annotations;

import io.github.aimtone.tentacolous.filter.TentacolousFilter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that changes to an entity's table must be captured and forwarded to the configured
 * {@link io.github.aimtone.tentacolous.sink.ChangeEventSink sinks} (Kafka, RabbitMQ, ...), even when
 * no {@code @Upon...} listener method exists for that entity.
 *
 * <p>Place it on any Spring-managed class, typically a {@code @Configuration}:
 * <pre>
 * &#64;Configuration
 * &#64;TentacolousCapture(entity = Person.class)
 * &#64;TentacolousCapture(
 *     entity = Order.class,
 *     actions = {ActionListener.INSERT, ActionListener.UPDATE},
 *     field = "status", valueType = ValueType.STRING, value = "APPROVED")
 * public class CaptureConfig { }
 * </pre>
 *
 * The equivalent programmatic form is a {@link io.github.aimtone.tentacolous.capture.Capture} bean.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(TentacolousCaptures.class)
public @interface TentacolousCapture {

    /** Entity whose table is captured. Table name and key are resolved from JPA annotations, as with listeners. */
    Class<?> entity();

    /** Operations to capture. Empty means all of {@code INSERT}, {@code UPDATE} and {@code DELETE}. */
    ActionListener[] actions() default {};

    /** Optional event entity name. Defaults to the entity class simple name. */
    String entityName() default "";

    /** Columns removed from the payload before it is stored and forwarded. */
    String[] exclude() default {};

    /** Declarative filter field. Only changes where this field equals {@link #value()} are forwarded. */
    String field() default "";

    /** Type used to interpret {@link #value()}. */
    ValueType valueType() default ValueType.NONE;

    /** Expected value, always written as text. */
    String value() default "";

    /** Spring bean extending {@link TentacolousFilter}. Takes precedence over the declarative filter. */
    Class<? extends TentacolousFilter<?>> filter() default TentacolousFilter.None.class;

    /** Ordering hint relative to other captures and listeners. Lower runs first. */
    int order() default 0;
}

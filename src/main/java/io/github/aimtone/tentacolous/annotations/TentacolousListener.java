package io.github.aimtone.tentacolous.annotations;

import io.github.aimtone.tentacolous.filter.TentacolousFilter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TentacolousListener {
    Class<?> entity();

    ActionListener action();

    /**
     * Optional event entity name. Defaults to the entity class simple name.
     */
    String entityName() default "";

    String field() default "";

    ValueType valueType() default ValueType.NONE;

    String value() default "";

    Class<? extends TentacolousFilter<?>> filter() default TentacolousFilter.None.class;

    /** Lower values are invoked first when multiple listeners match the same event. */
    int order() default 0;

    String[] exclude() default {};
}

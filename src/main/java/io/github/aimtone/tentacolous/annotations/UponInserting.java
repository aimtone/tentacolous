package io.github.aimtone.tentacolous.annotations;

import io.github.aimtone.tentacolous.filter.TentacolousFilter;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UponInserting {
    Class<?> entity();

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

package com.aimtone.tentacolous.annotations;

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

    String[] exclude() default {};
}

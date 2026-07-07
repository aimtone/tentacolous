package com.aimtone.tentacolous.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UponUpdating {
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

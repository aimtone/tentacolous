package io.github.aimtone.tentacolous.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Container annotation that holds repeated {@link TentacolousCapture} declarations. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TentacolousCaptures {

    TentacolousCapture[] value();
}

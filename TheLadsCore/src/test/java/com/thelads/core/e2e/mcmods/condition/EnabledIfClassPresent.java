package com.thelads.core.e2e.mcmods.condition;

import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to conditionally enable tests if a specific class is present on the classpath.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ClassPresenceCondition.class)
public @interface EnabledIfClassPresent {
    String[] value();
}

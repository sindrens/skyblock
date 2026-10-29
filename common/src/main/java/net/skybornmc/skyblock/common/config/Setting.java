package net.skybornmc.skyblock.common.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field of a {@link ModuleConfig} as a setting. The field's initial value is the default.
 * Supported types: boolean, int, long, double, String, List&lt;String&gt; and enums.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Setting {

    /** YAML path (dots for sections). Empty = field name in kebab-case. */
    String path() default "";

    /** Shown as a comment in the file and in the in-game /admin editor. */
    String desc();

    /** Inclusive minimum for numbers. */
    double min() default Double.NEGATIVE_INFINITY;

    /** Inclusive maximum for numbers. */
    double max() default Double.POSITIVE_INFINITY;

    /** True if a change only takes effect after a restart (the editor warns about it). */
    boolean restart() default false;

    /** Permission level needed to edit in-game: admin or owner (economy/season settings). */
    Level level() default Level.ADMIN;

    enum Level { ADMIN, OWNER }
}

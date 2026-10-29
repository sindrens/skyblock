package dev.slickymatt.skyblock.common.module;

import java.util.Set;

/**
 * A self-contained gameplay feature (islands, economy, depths, ...).
 *
 * <p>Modules never reach into each other's internals. They communicate through
 * services and Bukkit events. A module declares the ids of modules it depends on;
 * the {@link ModuleRegistry} enables dependencies first and disables in reverse order.</p>
 */
public interface SkyblockModule {

    /** Unique, lowercase id, e.g. {@code "islands"}. Used in config paths and logs. */
    String id();

    /** Ids of modules that must be enabled before this one. */
    default Set<String> dependencies() {
        return Set.of();
    }

    /** Called once on plugin enable, in dependency order. Throwing disables this module (and its dependents). */
    void enable() throws Exception;

    /** Called on plugin disable, in reverse order. Must save all state and never throw. */
    void disable();

    /** Reload config and messages without a restart. */
    default void reload() throws Exception {
    }
}

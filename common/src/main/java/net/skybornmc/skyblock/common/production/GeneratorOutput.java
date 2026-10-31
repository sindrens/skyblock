package net.skybornmc.skyblock.common.production;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

/**
 * The blocks a custom island generator can form besides cobblestone and stone (PHASE-7 M7.2: coal ore … the custom ore
 * blocks). The production module fills it while custom generators are on and empties it when they go off.
 *
 * <p>Why it exists: the generator changes the formed block inside {@code BlockFormEvent} (priority HIGH), so listeners that
 * run later (island stats {@code GENERATOR_OUTPUT}, the wardrobe's generator-block memory at MONITOR) see e.g. coal ore
 * instead of cobblestone. They ask {@link #contains} next to their own block list, so generator output keeps counting.
 * A lookup on the hot form path, so it is a plain static set (any thread reads a consistent copy).</p>
 */
public final class GeneratorOutput {

    private static volatile Set<Material> blocks = Set.of();

    private GeneratorOutput() {
    }

    /** True when a lava + water generator on an island may have formed this block type (custom generators on). */
    public static boolean contains(Material type) {
        return type != null && blocks.contains(type);
    }

    /** Set by the production module ({@code null} or empty = custom generators off). */
    public static void set(Set<Material> types) {
        blocks = types == null || types.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(types));
    }
}

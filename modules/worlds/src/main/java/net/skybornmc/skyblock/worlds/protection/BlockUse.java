package net.skybornmc.skyblock.worlds.protection;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sorts right-clicked blocks by material key (pure, unit-tested): free to use ({@code protection.interact-allowed}:
 * doors, buttons, levers...), container, workstation, or anything else. Patterns: exact key, {@code *suffix} or
 * {@code prefix*}. The free list is checked first. Results are cached per key (main thread only).
 */
public final class BlockUse {

    public enum Use { ALLOWED, CONTAINER, WORKSTATION, OTHER }

    private final List<String> allowed;
    private final List<String> containers;
    private final List<String> workstations;
    private final Map<String, Use> cache = new HashMap<>();

    public BlockUse(List<String> allowed, List<String> containers, List<String> workstations) {
        this.allowed = lower(allowed);
        this.containers = lower(containers);
        this.workstations = lower(workstations);
    }

    private static List<String> lower(List<String> patterns) {
        return patterns.stream().map(p -> p.trim().toLowerCase(Locale.ROOT)).filter(p -> !p.isEmpty()).toList();
    }

    /** Category of a block by its material key ({@code oak_door}, {@code minecraft:chest} works too). */
    public Use classify(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        int colon = k.indexOf(':');
        String name = colon >= 0 ? k.substring(colon + 1) : k;
        return cache.computeIfAbsent(name, n -> matches(allowed, n) ? Use.ALLOWED
                : matches(containers, n) ? Use.CONTAINER
                : matches(workstations, n) ? Use.WORKSTATION
                : Use.OTHER);
    }

    static boolean matches(List<String> patterns, String name) {
        for (String p : patterns) {
            if (p.startsWith("*") ? name.endsWith(p.substring(1))
                    : p.endsWith("*") ? name.startsWith(p.substring(0, p.length() - 1))
                    : name.equals(p)) {
                return true;
            }
        }
        return false;
    }
}

package net.skybornmc.skyblock.common.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Value changes for catalog files that have their own loader (sell.yml, shop.yml), the way {@code valueChanges()} works
 * for typed configs: a bundled default that changed in catalog version N replaces the live value only when the live
 * value is still the old default (an owner edit stays). The file's {@code catalog-version} (top level) records it, so a
 * step runs once. Pure (unit-tested); the caller saves the file when the result is not empty.
 */
public final class CatalogUpdate {

    /** The top-level key holding the catalog version. */
    public static final String KEY = "catalog-version";

    private CatalogUpdate() {
    }

    /** One changed default: {@code from} (the old bundled value) to {@code to} (null = the entry is removed). Numbers compare by value. */
    public record Change(Object from, Object to) {
        public static Change of(Object from, Object to) {
            return new Change(from, to);
        }
    }

    /** The catalog version of a file (0 when it has none). */
    public static int version(ConfigurationSection yaml) {
        return yaml.getInt(KEY, 0);
    }

    /**
     * Applies every step above the file's version up to {@code steps}' highest, in order, and sets the version. Returns
     * one line per changed value ("path: old -> new"), empty when nothing changed (the version may still have moved).
     * A path that is missing in the live file is skipped (the loader's add-missing step brings the new default).
     */
    public static List<String> apply(ConfigurationSection yaml, Map<Integer, Map<String, Change>> steps) {
        List<String> log = new ArrayList<>();
        int have = version(yaml);
        int top = have;
        for (int v : steps.keySet().stream().sorted().toList()) {
            if (v <= have) {
                continue;
            }
            for (Map.Entry<String, Change> e : steps.get(v).entrySet()) {
                String path = e.getKey();
                if (!yaml.contains(path)) {
                    continue;
                }
                Object live = yaml.get(path);
                if (same(live, e.getValue().from())) {
                    yaml.set(path, e.getValue().to());
                    log.add(path + ": " + live + " -> " + e.getValue().to());
                }
            }
            top = Math.max(top, v);
        }
        if (top > have) {
            yaml.set(KEY, top);
        }
        return log;
    }

    static boolean same(Object live, Object old) {
        if (live instanceof Number a && old instanceof Number b) {
            return Math.abs(a.doubleValue() - b.doubleValue()) < 1e-9;
        }
        return live != null && live.equals(old);
    }
}

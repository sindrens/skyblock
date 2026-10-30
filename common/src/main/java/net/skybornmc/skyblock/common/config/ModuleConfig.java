package net.skybornmc.skyblock.common.config;

import java.util.Map;

/**
 * Base class for a typed config file. Subclasses declare {@link Setting} fields with defaults.
 * Loaded and saved by {@link ConfigManager}.
 */
public abstract class ModuleConfig {

    /** File name inside the plugin data folder, e.g. {@code "core.yml"}. */
    public abstract String fileName();

    /** Current schema version. Increase it when keys are renamed and add a migration. */
    public int version() {
        return 1;
    }

    /** Renamed keys per version step: old path → new path, applied when upgrading from (version - 1). */
    public Map<Integer, Map<String, String>> migrations() {
        return Map.of();
    }

    /**
     * Changed defaults per version step: path → (old default, new default), applied when upgrading from (version - 1).
     * A value is only changed while it still equals the old default, so a value the owner chose is kept.
     */
    public Map<Integer, Map<String, ValueChange>> valueChanges() {
        return Map.of();
    }

    /** One changed default: the file's value is replaced by {@code to} only while it equals {@code from}. */
    public record ValueChange(Object from, Object to) {

        public static ValueChange of(Object from, Object to) {
            return new ValueChange(from, to);
        }

        /** True when {@code current} is the old default (numbers compare by value, 3 equals 3.0). */
        public boolean matches(Object current) {
            if (current instanceof Number a && from instanceof Number b) {
                return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
            }
            return java.util.Objects.equals(current, from)
                    || (current != null && from != null && String.valueOf(current).equals(String.valueOf(from)));
        }
    }

    /** Optional header comment for the file. */
    public String header() {
        return "";
    }
}

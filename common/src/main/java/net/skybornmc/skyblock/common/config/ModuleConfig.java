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

    /** Optional header comment for the file. */
    public String header() {
        return "";
    }
}

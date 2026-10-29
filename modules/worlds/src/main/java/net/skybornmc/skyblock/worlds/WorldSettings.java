package net.skybornmc.skyblock.worlds;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Settings for one world, parsed from {@code worlds.yml}. Pure data: no server access, so it is unit-testable.
 *
 * @param name       world folder name
 * @param create     true if this module creates the world (false for the server's main world)
 * @param biome      vanilla biome id used for the whole void world
 * @param difficulty Bukkit difficulty name (PEACEFUL, EASY, NORMAL, HARD)
 * @param spawnX     spawn x
 * @param spawnY     spawn y
 * @param spawnZ     spawn z
 * @param spawnYaw   spawn yaw
 * @param platform   block id placed under the spawn if empty, or null for none
 * @param border     world border diameter, 0 = none
 * @param time       fixed time of day, or null to leave unchanged
 * @param gamerules  vanilla game rule id to value (defaults merged in)
 */
public record WorldSettings(
        String name,
        boolean create,
        String biome,
        String difficulty,
        double spawnX,
        double spawnY,
        double spawnZ,
        float spawnYaw,
        String platform,
        double border,
        Long time,
        Map<String, Object> gamerules
) {

    public WorldSettings {
        Objects.requireNonNull(name, "name");
        if (!name.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid world name '" + name + "' (use lowercase letters, digits, _ or -)");
        }
        if (border < 0) {
            throw new IllegalArgumentException("World '" + name + "': border must be >= 0");
        }
        if (time != null && (time < 0 || time > 24000)) {
            throw new IllegalArgumentException("World '" + name + "': time must be between 0 and 24000");
        }
        gamerules = Collections.unmodifiableMap(new LinkedHashMap<>(gamerules));
    }

    /**
     * Parses one world section, merging the shared default game rules underneath the world's own.
     */
    public static WorldSettings parse(String name, ConfigurationSection section, Map<String, Object> defaultGamerules) {
        Map<String, Object> rules = new LinkedHashMap<>(defaultGamerules);
        ConfigurationSection rulesSection = section.getConfigurationSection("gamerules");
        if (rulesSection != null) {
            for (String key : rulesSection.getKeys(false)) {
                rules.put(key, rulesSection.get(key));
            }
        }
        ConfigurationSection spawn = section.getConfigurationSection("spawn");
        Object time = section.get("time");
        return new WorldSettings(
                name,
                section.getBoolean("create", true),
                section.getString("biome", "the_void").toLowerCase(Locale.ROOT),
                section.getString("difficulty", "normal").toUpperCase(Locale.ROOT),
                spawn == null ? 0.5 : spawn.getDouble("x", 0.5),
                spawn == null ? 100 : spawn.getDouble("y", 100),
                spawn == null ? 0.5 : spawn.getDouble("z", 0.5),
                spawn == null ? 0f : (float) spawn.getDouble("yaw", 0),
                section.getString("platform"),
                section.getDouble("border", 0),
                time instanceof Number n ? n.longValue() : null,
                rules
        );
    }

    /** Reads the shared {@code defaults.gamerules} section. */
    public static Map<String, Object> parseDefaults(ConfigurationSection root) {
        Map<String, Object> rules = new LinkedHashMap<>();
        ConfigurationSection section = root.getConfigurationSection("defaults.gamerules");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                rules.put(key, section.get(key));
            }
        }
        return rules;
    }
}

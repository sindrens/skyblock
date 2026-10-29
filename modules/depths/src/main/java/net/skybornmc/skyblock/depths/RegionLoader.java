package net.skybornmc.skyblock.depths;

import net.skybornmc.skyblock.depths.engine.DepthsRegion;
import net.skybornmc.skyblock.depths.engine.RegionIndex;
import net.skybornmc.skyblock.depths.engine.ResourceBlock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads the {@code regions} section of {@code depths.yml}:
 * <pre>
 * regions:
 *   iron_field:
 *     world: depths
 *     corner-1: "0, 60, 0"
 *     corner-2: "50, 90, 50"
 *     resources:
 *       - { block: iron_ore, depleted: tuff, respawn-seconds: 10 }
 * </pre>
 * Broken entries are reported and skipped; the rest still load.
 */
final class RegionLoader {

    static final String SECTION = "regions";
    static final String SOURCE = "config";

    private RegionLoader() {
    }

    /** Loads all regions from the file (adds an empty {@code regions:} section with a comment if missing). */
    static List<DepthsRegion> load(File file, DepthsConfig config, Consumer<String> problem) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            problem.accept(file.getName() + ": not valid YAML, no regions loaded. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return List.of();
        }
        if (!yaml.contains(SECTION)) {
            yaml.createSection(SECTION);
            yaml.setComments(SECTION, List.of("Per-player resource regions (see the header). Empty until the Depths is built."));
            try {
                yaml.save(file);
            } catch (IOException e) {
                problem.accept(file.getName() + ": could not add the regions section: " + e.getMessage());
            }
            return List.of();
        }
        ConfigurationSection section = yaml.getConfigurationSection(SECTION);
        if (section == null) {
            problem.accept(file.getName() + " → regions: must be a section of regions, nothing loaded");
            return List.of();
        }
        List<DepthsRegion> regions = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            String where = file.getName() + " → regions." + id;
            ConfigurationSection r = section.getConfigurationSection(id);
            if (r == null) {
                problem.accept(where + ": not a section, skipped");
                continue;
            }
            try {
                DepthsRegion region = parse(id, r, config, where, problem);
                RegionIndex.of(List.of(region)); // size check
                regions.add(region);
                if (Bukkit.getWorld(region.world()) == null) {
                    problem.accept(where + ".world: world '" + region.world() + "' is not loaded (the region works once it is)");
                }
            } catch (IllegalArgumentException e) {
                problem.accept(where + ": " + e.getMessage() + ", skipped");
            }
        }
        return regions;
    }

    private static DepthsRegion parse(String id, ConfigurationSection r, DepthsConfig config, String where,
                                      Consumer<String> problem) {
        String world = r.getString("world");
        if (world == null || world.isBlank()) {
            throw new IllegalArgumentException("missing 'world'");
        }
        int[] a = corner(r.get("corner-1"), "corner-1");
        int[] b = corner(r.get("corner-2"), "corner-2");
        Map<Material, ResourceBlock> resources = new EnumMap<>(Material.class);
        List<Map<?, ?>> entries = r.getMapList("resources");
        if (entries.isEmpty()) {
            problem.accept(where + ".resources: no resource blocks (players can't mine anything here)");
        }
        for (int i = 0; i < entries.size(); i++) {
            Map<?, ?> entry = entries.get(i);
            String at = where + ".resources[" + (i + 1) + "]";
            try {
                ResourceBlock res = resource(entry, config);
                if (resources.putIfAbsent(res.block(), res) != null) {
                    problem.accept(at + ": " + key(res.block()) + " is listed twice, the first entry is used");
                }
            } catch (IllegalArgumentException e) {
                problem.accept(at + ": " + e.getMessage() + ", skipped");
            }
        }
        return DepthsRegion.of(id.toLowerCase(Locale.ROOT), world, a[0], a[1], a[2], b[0], b[1], b[2], resources, SOURCE);
    }

    private static ResourceBlock resource(Map<?, ?> entry, DepthsConfig config) {
        Object blockRaw = entry.get("block");
        if (blockRaw == null) {
            throw new IllegalArgumentException("missing 'block'");
        }
        Material block = Material.matchMaterial(String.valueOf(blockRaw));
        if (block == null || !block.isBlock()) {
            throw new IllegalArgumentException("'" + blockRaw + "' is not a block");
        }
        Object depletedRaw = entry.get("depleted");
        String depletedText = depletedRaw == null ? config.defaultDepleted : String.valueOf(depletedRaw);
        BlockData depleted = blockData(depletedText);
        int seconds = config.defaultRespawnSeconds;
        Object secondsRaw = entry.get("respawn-seconds");
        if (secondsRaw != null) {
            seconds = respawnSeconds(secondsRaw);
        }
        return new ResourceBlock(block, depleted, seconds * 20);
    }

    /** Parses a block id or full block state ({@code tuff}, {@code minecraft:oak_log[axis=y]}). */
    static BlockData blockData(String text) {
        String id = text.trim().toLowerCase(Locale.ROOT);
        try {
            BlockData data = Bukkit.createBlockData(id.contains(":") ? id : "minecraft:" + id);
            if (!data.getMaterial().isBlock()) {
                throw new IllegalArgumentException("'" + text + "' is not a block");
            }
            return data;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + text + "' is not a valid block (depleted)");
        }
    }

    /** {@code respawn-seconds}: a whole number from 1 to 3600. */
    static int respawnSeconds(Object raw) {
        try {
            int s = raw instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(raw).trim());
            if (s >= 1 && s <= 3600) {
                return s;
            }
        } catch (NumberFormatException ignored) {
            // falls through
        }
        throw new IllegalArgumentException("respawn-seconds '" + raw + "' must be a whole number from 1 to 3600");
    }

    /** A corner as {@code "x, y, z"}, {@code "x y z"} or a list {@code [x, y, z]}. */
    static int[] corner(Object raw, String name) {
        if (raw == null) {
            throw new IllegalArgumentException("missing '" + name + "' (write it as \"x, y, z\")");
        }
        List<String> parts = new ArrayList<>();
        if (raw instanceof List<?> list) {
            list.forEach(o -> parts.add(String.valueOf(o).trim()));
        } else {
            for (String p : String.valueOf(raw).trim().split("[,\\s]+")) {
                if (!p.isEmpty()) {
                    parts.add(p);
                }
            }
        }
        if (parts.size() != 3) {
            throw new IllegalArgumentException("'" + name + "' must be three numbers x, y, z (got '" + raw + "')");
        }
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            try {
                out[i] = (int) Math.floor(Double.parseDouble(parts.get(i)));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + name + "' must be three numbers x, y, z (got '" + raw + "')");
            }
        }
        if (out[1] < -2048 || out[1] > 2047) {
            throw new IllegalArgumentException("'" + name + "' y must be between -2048 and 2047");
        }
        return out;
    }

    private static String key(Material m) {
        return m.name().toLowerCase(Locale.ROOT);
    }
}

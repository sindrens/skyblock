package net.skybornmc.skyblock.menus;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The {@code /menu → Warps} list from {@code menu.yml → warps} (UX3): server places with an icon and a target. Texts are
 * in {@code messages/navigation.yml → warps.entries.<id>}. Pure parsing; where a target leads is resolved when the menu
 * opens ({@link WarpsMenu}), and entries whose place does not exist on this server are hidden.
 */
public record WarpLayout(List<Warp> warps) {

    /** Where a warp leads. */
    public sealed interface Target permits Command, Point, Place {
    }

    /** Runs a command as the player (e.g. {@code spawn}, {@code is warp}); shown only when the command exists. */
    public record Command(String command) implements Target {
    }

    /** A build point ({@code build-points.yml}, e.g. {@code entries tavern_road}); shown only when the build is applied. */
    public record Point(String category, String id) implements Target {
    }

    /** A fixed position; shown only when the world is loaded. */
    public record Place(String world, double x, double y, double z, float yaw, float pitch) implements Target {
    }

    /**
     * @param reason teleport reason id (warmup and cooldown of {@code teleport.yml}): {@code spawn} or {@code dungeon}
     */
    public record Warp(String id, int slot, Material icon, Target target, String reason, String permission) {
    }

    public static final String DEFAULT_REASON = "spawn";
    static final Set<String> REASONS = Set.of("spawn", "dungeon");

    static WarpLayout parse(YamlConfiguration yaml, Consumer<String> problem) {
        List<Warp> out = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        ConfigurationSection section = yaml.getConfigurationSection("warps");
        if (section == null) {
            return new WarpLayout(List.of());
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection e = section.getConfigurationSection(id);
            String where = "menu.yml → warps." + id;
            if (e == null) {
                problem.accept(where + ": needs slot, icon and target (skipped)");
                continue;
            }
            int slot = e.getInt("slot", -1);
            if (slot < 0 || slot >= 54 || HubLayout.RESERVED.contains(slot)) {
                problem.accept(where + ".slot: " + slot + " is not an inner slot of the menu (skipped)");
                continue;
            }
            if (!used.add(slot)) {
                problem.accept(where + ".slot: slot " + slot + " is already used (skipped)");
                continue;
            }
            Target target = target(e.getString("target", ""));
            if (target == null) {
                problem.accept(where + ".target: '" + e.getString("target", "") + "' is not 'command <cmd>', 'point <category> <id>' "
                        + "or 'location <world> <x> <y> <z> [yaw] [pitch]' (skipped)");
                used.remove(slot);
                continue;
            }
            String iconName = e.getString("icon", "ender_pearl");
            Material icon = Material.matchMaterial(iconName);
            if (icon == null || !icon.isItem()) {
                problem.accept(where + ".icon: unknown item '" + iconName + "' (using ender pearl)");
                icon = Material.ENDER_PEARL;
            }
            String reason = e.getString("reason", DEFAULT_REASON).toLowerCase(Locale.ROOT);
            if (!REASONS.contains(reason)) {
                problem.accept(where + ".reason: '" + reason + "' is not spawn or dungeon (using spawn)");
                reason = DEFAULT_REASON;
            }
            out.add(new Warp(id, slot, icon, target, reason, e.getString("permission")));
        }
        return new WarpLayout(List.copyOf(out));
    }

    /** Parses {@code command <cmd...>}, {@code point <category> <id>} or {@code location <world> <x> <y> <z> [yaw] [pitch]}; null if invalid. */
    static Target target(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.strip().split("\\s+");
        if (parts.length < 2) {
            return null;
        }
        switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "command" -> {
                String command = raw.strip().substring(parts[0].length()).strip();
                return command.startsWith("/") ? (command.length() > 1 ? new Command(command.substring(1)) : null) : new Command(command);
            }
            case "point" -> {
                return parts.length == 3 ? new Point(parts[1], parts[2]) : null;
            }
            case "location" -> {
                if (parts.length != 5 && parts.length != 7) {
                    return null;
                }
                try {
                    double x = Double.parseDouble(parts[2]);
                    double y = Double.parseDouble(parts[3]);
                    double z = Double.parseDouble(parts[4]);
                    float yaw = parts.length == 7 ? Float.parseFloat(parts[5]) : 0f;
                    float pitch = parts.length == 7 ? Float.parseFloat(parts[6]) : 0f;
                    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                        return null;
                    }
                    return new Place(parts[1], x, y, z, yaw, Math.max(-90f, Math.min(90f, pitch)));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            default -> {
                return null;
            }
        }
    }

    /** Build points of road entries are block positions ({@code x: -46}): stand in the middle of the block. */
    static double centre(double v) {
        return v == Math.floor(v) ? v + 0.5 : v;
    }
}

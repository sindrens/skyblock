package net.skybornmc.skyblock.depths.zones;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reads {@code zones.yml} into a {@link ZoneSet}. Broken entries are reported through {@code problem} and skipped (or
 * the broken optional part is dropped); everything else still loads. Block ids are kept as text here: the module
 * resolves them against the server's block list.
 */
public final class ZoneParser {

    /** Palette roles in the order they are written. */
    static final List<String> PALETTE_ORDER = List.of("floor", "inlay", "base", "wall", "trim", "light", "pad", "fire",
            "ground", "leaves", "arena", "gate-mine", "gate-forest", "gate-combat");

    private static final String ID_PATTERN = "[a-z0-9_-]+";

    private ZoneParser() {
    }

    public static ZoneSet parse(ConfigurationSection root, String file, Consumer<String> problem) {
        String world = root.getString("world", "depths");
        int floorY = root.getInt("floor-y", 100);
        Hub hub = null;
        ConfigurationSection h = root.getConfigurationSection("hub");
        if (h != null) {
            try {
                hub = hub(h, file + " → hub", problem);
            } catch (IllegalArgumentException e) {
                problem.accept(file + " → hub: " + e.getMessage() + ", The Gate is skipped");
            }
        }
        BossArena arena = null;
        ConfigurationSection a = root.getConfigurationSection("boss-arena");
        if (a != null) {
            try {
                arena = arena(a);
            } catch (IllegalArgumentException e) {
                problem.accept(file + " → boss-arena: " + e.getMessage() + ", the boss arena is skipped");
            }
        }
        List<Zone> zones = new ArrayList<>();
        ConfigurationSection zs = root.getConfigurationSection("zones");
        if (zs == null) {
            problem.accept(file + ": no 'zones' section, no tier zones loaded");
        } else {
            Set<String> seenTiers = new HashSet<>();
            for (String id : zs.getKeys(false)) {
                String where = file + " → zones." + id;
                ConfigurationSection z = zs.getConfigurationSection(id);
                if (z == null) {
                    problem.accept(where + ": not a section, skipped");
                    continue;
                }
                try {
                    Zone zone = zone(id, z, where, problem);
                    if (!seenTiers.add(zone.aspect().id() + ":" + zone.tier())) {
                        problem.accept(where + ": " + zone.aspect().id() + " tier " + zone.tier() + " is defined twice, skipped");
                        continue;
                    }
                    zones.add(zone);
                } catch (IllegalArgumentException e) {
                    problem.accept(where + ": " + e.getMessage() + ", skipped");
                }
            }
        }
        return new ZoneSet(world, floorY, hub, arena, zones);
    }

    private static Hub hub(ConfigurationSection h, String where, Consumer<String> problem) {
        Map<Aspect, Box> gates = new EnumMap<>(Aspect.class);
        ConfigurationSection g = h.getConfigurationSection("wing-gates");
        if (g != null) {
            for (String key : g.getKeys(false)) {
                Aspect aspect = Aspect.byId(key).orElse(null);
                if (aspect == null) {
                    problem.accept(where + ".wing-gates." + key + ": unknown wing (mine, forest, combat), skipped");
                    continue;
                }
                try {
                    gates.put(aspect, box(g, key));
                } catch (IllegalArgumentException e) {
                    problem.accept(where + ".wing-gates." + key + ": " + e.getMessage() + ", skipped");
                }
            }
        }
        return new Hub(h.getString("name", "The Gate"), h.getBoolean("built", false), box(h, "region"),
                point(h.get("spawn"), "spawn"), gates, palette(h));
    }

    private static BossArena arena(ConfigurationSection a) {
        String portalZone = a.getString("portal-zone");
        return new BossArena(a.getString("name", "World Boss Arena"), a.getBoolean("built", false), box(a, "region"),
                point(a.get("spawn"), "spawn"), portalZone == null ? null : portalZone.toLowerCase(Locale.ROOT),
                optionalBox(a, "portal"), optionalBox(a, "return-portal"), palette(a));
    }

    private static Zone zone(String rawId, ConfigurationSection z, String where, Consumer<String> problem) {
        String id = rawId.toLowerCase(Locale.ROOT);
        if (!id.matches(ID_PATTERN)) {
            throw new IllegalArgumentException("id must use a-z, 0-9, _ and -");
        }
        String aspectRaw = z.getString("aspect");
        Aspect aspect = Aspect.byId(aspectRaw)
                .orElseThrow(() -> new IllegalArgumentException("aspect '" + aspectRaw + "' must be mine, forest or combat"));
        int tier = z.getInt("tier", 0);
        if (tier < 1) {
            throw new IllegalArgumentException("tier must be 1 or higher");
        }
        int level = z.getInt("required-level", 0);
        if (level < 0) {
            problem.accept(where + ".required-level: negative, 0 is used");
            level = 0;
        }
        Box region = box(z, "region");
        Point spawn = point(z.get("spawn"), "spawn");
        Box gate = box(z, "gate");
        Box exit = optional(z, "exit", where, problem);
        Box causeway = optional(z, "causeway", where, problem);
        Box arena = optional(z, "arena", where, problem);
        List<Field> fields = new ArrayList<>();
        ConfigurationSection fs = z.getConfigurationSection("fields");
        if (fs != null) {
            for (String fieldId : fs.getKeys(false)) {
                String at = where + ".fields." + fieldId;
                ConfigurationSection f = fs.getConfigurationSection(fieldId);
                if (f == null) {
                    problem.accept(at + ": not a section, skipped");
                    continue;
                }
                try {
                    fields.add(field(fieldId.toLowerCase(Locale.ROOT), f, at, problem));
                } catch (IllegalArgumentException e) {
                    problem.accept(at + ": " + e.getMessage() + ", skipped");
                }
            }
        }
        String biome = z.getString("biome");
        return new Zone(id, aspect, tier, z.getString("name", id), z.getString("resource-name", ""), level,
                z.getBoolean("built", false), region, spawn, gate, exit, causeway, fields, arena,
                biome == null || biome.isBlank() ? null : biome.trim().toLowerCase(Locale.ROOT), palette(z));
    }

    private static Field field(String id, ConfigurationSection f, String where, Consumer<String> problem) {
        if (!id.matches(ID_PATTERN)) {
            throw new IllegalArgumentException("id must use a-z, 0-9, _ and -");
        }
        int[] c1 = corner(f.get("corner-1"), "corner-1");
        int[] c2 = corner(f.get("corner-2"), "corner-2");
        Box box = Box.of(c1[0], c1[1], c1[2], c2[0], c2[1], c2[2]);
        List<Field.Resource> resources = new ArrayList<>();
        List<Map<?, ?>> entries = f.getMapList("resources");
        for (int i = 0; i < entries.size(); i++) {
            Map<?, ?> e = entries.get(i);
            Object block = e.get("block");
            if (block == null || String.valueOf(block).isBlank()) {
                problem.accept(where + ".resources[" + (i + 1) + "]: missing 'block', skipped");
                continue;
            }
            Object depleted = e.get("depleted");
            Integer seconds = null;
            Object s = e.get("respawn-seconds");
            if (s != null) {
                try {
                    seconds = s instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(s).trim());
                    if (seconds < 1 || seconds > 3600) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException ex) {
                    problem.accept(where + ".resources[" + (i + 1) + "]: respawn-seconds '" + s
                            + "' must be a whole number from 1 to 3600, the default is used");
                    seconds = null;
                }
            }
            resources.add(new Field.Resource(normalize(String.valueOf(block)),
                    depleted == null ? null : normalize(String.valueOf(depleted)), seconds));
        }
        if (resources.isEmpty()) {
            problem.accept(where + ".resources: no resource blocks (players can't mine anything here)");
        }
        return new Field(id, box, resources);
    }

    private static Map<String, String> palette(ConfigurationSection s) {
        ConfigurationSection p = s.getConfigurationSection("palette");
        Map<String, String> out = new LinkedHashMap<>();
        if (p != null) {
            for (String key : p.getKeys(false)) {
                String value = p.getString(key);
                if (value != null && !value.isBlank()) {
                    out.put(key.toLowerCase(Locale.ROOT), normalize(value));
                }
            }
        }
        return orderedPalette(out);
    }

    static Map<String, String> orderedPalette(Map<String, String> palette) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : PALETTE_ORDER) {
            if (palette.containsKey(key)) {
                out.put(key, palette.get(key));
            }
        }
        palette.forEach(out::putIfAbsent);
        return out;
    }

    /** Lowercase, trimmed, without the {@code minecraft:} namespace. */
    static String normalize(String block) {
        String s = block.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s;
    }

    private static Box optional(ConfigurationSection s, String key, String where, Consumer<String> problem) {
        if (!s.contains(key)) {
            return null;
        }
        try {
            return box(s, key);
        } catch (IllegalArgumentException e) {
            problem.accept(where + "." + key + ": " + e.getMessage() + ", ignored");
            return null;
        }
    }

    private static Box optionalBox(ConfigurationSection s, String key) {
        return s.contains(key) ? box(s, key) : null;
    }

    static Box box(ConfigurationSection parent, String key) {
        ConfigurationSection b = parent.getConfigurationSection(key);
        if (b == null) {
            throw new IllegalArgumentException("missing '" + key + "' (write it as { corner-1: \"x, y, z\", corner-2: \"x, y, z\" })");
        }
        int[] c1 = corner(b.get("corner-1"), key + ".corner-1");
        int[] c2 = corner(b.get("corner-2"), key + ".corner-2");
        return Box.of(c1[0], c1[1], c1[2], c2[0], c2[1], c2[2]);
    }

    /** A corner as {@code "x, y, z"}, {@code "x y z"} or a list {@code [x, y, z]}. */
    static int[] corner(Object raw, String name) {
        double[] v = numbers(raw, name, 3, 3);
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            out[i] = (int) Math.floor(v[i]);
        }
        if (out[1] < -2048 || out[1] > 2047) {
            throw new IllegalArgumentException("'" + name + "' y must be between -2048 and 2047");
        }
        return out;
    }

    /** A point as {@code "x, y, z[, yaw[, pitch]]"}. */
    static Point point(Object raw, String name) {
        double[] v = numbers(raw, name, 3, 5);
        return new Point(v[0], v[1], v[2], v.length > 3 ? (float) v[3] : 0f, v.length > 4 ? (float) v[4] : 0f);
    }

    private static double[] numbers(Object raw, String name, int min, int max) {
        if (raw == null) {
            throw new IllegalArgumentException("missing '" + name + "'");
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
        if (parts.size() < min || parts.size() > max) {
            throw new IllegalArgumentException("'" + name + "' must be " + (min == max ? min : min + " to " + max)
                    + " numbers (got '" + raw + "')");
        }
        double[] out = new double[parts.size()];
        for (int i = 0; i < out.length; i++) {
            try {
                out[i] = Double.parseDouble(parts.get(i));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + name + "' must be numbers (got '" + raw + "')");
            }
            if (!Double.isFinite(out[i])) {
                throw new IllegalArgumentException("'" + name + "' must be finite numbers");
            }
        }
        return out;
    }
}

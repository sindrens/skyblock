package net.skybornmc.skyblock.worlds.builds;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildRegion;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Parses a build metadata file. Known keys are read into fields; every other list of entries with coordinates becomes
 * {@link BuildPoint}s (entries with {@code x, y, z}) or {@link BuildRegion}s (entries with {@code from}/{@code to}), so
 * new categories from builders are kept without code changes. Broken entries are reported and skipped.
 */
public final class BuildMetaParser {

    /** Keys read as fields (not turned into points). */
    static final Set<String> FIELDS = Set.of("schematic", "world", "origin", "paste_air", "paste_biomes", "bounds", "tier",
            "stats", "notes", "footprint", "causeways", "underside_lowest_y");
    /** Lists turned into regions (their entries need from/to). */
    static final Set<String> REGION_LISTS = Set.of("regions", "portals");

    private BuildMetaParser() {
    }

    public static BuildMeta parse(String id, ConfigurationSection yaml, Consumer<String> problem) {
        String schematic = text(yaml.get("schematic"));
        String world = text(yaml.get("world"));
        if (world != null) {
            world = world.toLowerCase(Locale.ROOT);
        }
        int[] origin = null;
        if (yaml.get("origin") != null) {
            Map<String, Object> o = map(yaml.get("origin"));
            Double x = num(o.get("x"));
            Double y = num(o.get("y"));
            Double z = num(o.get("z"));
            if (x == null || y == null || z == null) {
                problem.accept("origin needs x, y and z");
            } else {
                origin = new int[]{(int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)};
            }
        }
        BuildBox bounds = null;
        if (yaml.get("bounds") != null) {
            bounds = box(map(yaml.get("bounds")));
            if (bounds == null) {
                problem.accept("bounds needs from {x, y, z} and to {x, y, z}");
            }
        }
        Map<String, String> tier = new LinkedHashMap<>();
        map(yaml.get("tier")).forEach((k, v) -> tier.put(k, String.valueOf(v)));
        long statsNonAir = -1;
        Double stat = num(map(yaml.get("stats")).get("blocks_non_air"));
        if (stat != null) {
            statsNonAir = stat.longValue();
        }
        String w = world == null ? "" : world;
        List<BuildPoint> points = new ArrayList<>();
        List<BuildRegion> regions = new ArrayList<>();
        BuildMeta.Spot playerSpawn = null;
        BuildMeta.Spot home = null;
        for (String key : yaml.getKeys(false)) {
            if (FIELDS.contains(key)) {
                continue;
            }
            Object value = yaml.get(key);
            if (value instanceof List<?> list) {
                readList(id, w, key, list, points, regions, problem);
            } else if (value instanceof ConfigurationSection || value instanceof Map<?, ?>) {
                Map<String, Object> entry = map(value);
                BuildMeta.Spot spot = spot(entry);
                if (spot == null) {
                    continue; // a plain section without coordinates (not a point)
                }
                points.add(point(id, w, key, key, entry, spot));
                if (key.equals("player_spawn")) {
                    playerSpawn = spot;
                } else if (key.equals("home")) {
                    home = spot;
                }
            }
        }
        if (yaml.get("player_spawn") != null && playerSpawn == null) {
            problem.accept("player_spawn needs x, y and z");
        }
        return new BuildMeta(id, schematic, world, origin, yaml.getBoolean("paste_air", true), yaml.getBoolean("paste_biomes", true),
                bounds, playerSpawn, home, tier, statsNonAir, points, regions);
    }

    private static void readList(String build, String world, String key, List<?> list, List<BuildPoint> points,
                                 List<BuildRegion> regions, Consumer<String> problem) {
        Set<String> ids = new LinkedHashSet<>();
        int index = 0;
        for (Object raw : list) {
            index++;
            if (!(raw instanceof Map<?, ?>) && !(raw instanceof ConfigurationSection)) {
                continue; // plain values (e.g. strings) are not spots
            }
            Map<String, Object> entry = map(raw);
            String id = text(entry.get("id"));
            if (id == null) {
                id = key + "_" + index;
            }
            if (!ids.add(id)) {
                problem.accept(key + ": id '" + id + "' is used twice, the second one is skipped");
                continue;
            }
            BuildBox box = entry.containsKey("from") && entry.containsKey("to") ? box(entry) : null;
            if (REGION_LISTS.contains(key)) {
                if (box == null) {
                    problem.accept(key + "." + id + ": needs from {x, y, z} and to {x, y, z}, skipped");
                    continue;
                }
                regions.add(new BuildRegion(build, key, id, text(entry.get("kind")), world, box, extra(entry, Set.of("id", "kind", "from", "to"))));
                continue;
            }
            BuildMeta.Spot spot = spot(entry);
            if (spot != null) {
                points.add(point(build, world, key, id, entry, spot));
            } else if (box != null) {
                regions.add(new BuildRegion(build, key, id, text(entry.get("kind")), world, box, extra(entry, Set.of("id", "kind", "from", "to"))));
            } else {
                problem.accept(key + "." + id + ": no x/y/z or from/to, skipped");
            }
        }
    }

    private static BuildPoint point(String build, String world, String category, String id, Map<String, Object> entry,
                                    BuildMeta.Spot spot) {
        return new BuildPoint(build, category, id, world, spot.x(), spot.y(), spot.z(), spot.yaw(), spot.pitch(),
                extra(entry, Set.of("id", "x", "y", "z", "yaw", "pitch")));
    }

    static BuildMeta.Spot spot(Map<String, Object> entry) {
        Double x = num(entry.get("x"));
        Double y = num(entry.get("y"));
        Double z = num(entry.get("z"));
        if (x == null || y == null || z == null) {
            return null;
        }
        Double yaw = num(entry.get("yaw"));
        Double pitch = num(entry.get("pitch"));
        boolean integer = x == Math.rint(x) && z == Math.rint(z) && !(entry.get("x") instanceof Double) && !(entry.get("z") instanceof Double);
        return new BuildMeta.Spot(x, y, z, yaw == null ? 0f : yaw.floatValue(), pitch == null ? 0f : pitch.floatValue(), integer);
    }

    /** A box from {@code from: {x,y,z}} and {@code to: {x,y,z}}; {@code null} if either is incomplete. */
    static BuildBox box(Map<String, Object> entry) {
        Map<String, Object> from = map(entry.get("from"));
        Map<String, Object> to = map(entry.get("to"));
        Double x1 = num(from.get("x"));
        Double y1 = num(from.get("y"));
        Double z1 = num(from.get("z"));
        Double x2 = num(to.get("x"));
        Double y2 = num(to.get("y"));
        Double z2 = num(to.get("z"));
        if (x1 == null || y1 == null || z1 == null || x2 == null || y2 == null || z2 == null) {
            return null;
        }
        return new BuildBox(x1.intValue(), y1.intValue(), z1.intValue(), x2.intValue(), y2.intValue(), z2.intValue());
    }

    private static Map<String, String> extra(Map<String, Object> entry, Set<String> skip) {
        Map<String, String> out = new LinkedHashMap<>();
        entry.forEach((k, v) -> {
            if (!skip.contains(k) && v != null) {
                out.put(k, flat(v));
            }
        });
        return out;
    }

    /** Compact text of a value; nested maps as {@code {x: 1, y: 2}}, lists as {@code [a, b]}. */
    static String flat(Object v) {
        if (v instanceof ConfigurationSection || v instanceof Map<?, ?>) {
            StringBuilder sb = new StringBuilder("{");
            map(v).forEach((k, x) -> sb.append(sb.length() > 1 ? ", " : "").append(k).append(": ").append(flat(x)));
            return sb.append('}').toString();
        }
        if (v instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (Object x : list) {
                sb.append(sb.length() > 1 ? ", " : "").append(flat(x));
            }
            return sb.append(']').toString();
        }
        if (v instanceof Double d && d == Math.rint(d) && !Double.isInfinite(d)) {
            return Long.toString(d.longValue());
        }
        return String.valueOf(v);
    }

    /** Section or map as a String-keyed map (empty for anything else). */
    static Map<String, Object> map(Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (value instanceof ConfigurationSection s) {
            for (String k : s.getKeys(false)) {
                out.put(k, s.get(k));
            }
        } else if (value instanceof Map<?, ?> m) {
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
        }
        return out;
    }

    static Double num(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static String text(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() || s.equals("null") ? null : s;
    }
}

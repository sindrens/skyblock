package net.skybornmc.skyblock.production;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The generator ores and level table ({@code generators.yml → ores / levels}, PHASE-7 M7.2), parsed without the server so
 * it is unit-tested. Block and item names stay strings here; the module resolves them to materials.
 *
 * <p>A roll takes one uniform number in {@code [0, 1)} and picks an ore by its weight at the island's level (the highest
 * listed level at or below it; a level below the lowest listed one forms plain cobble). O(ores) per roll, no allocation.</p>
 */
final class GeneratorTable {

    /** The id whose block is "keep the block that formed" when nothing else is configured. */
    static final String KEEP = "keep";

    /**
     * One generator block.
     *
     * @param block    block id ({@code coal_ore}) or {@link #KEEP}
     * @param tier     matching Depths mine tier (0 = not an ore: cobble)
     * @param depthsXp XP of the matching Depths ore (pillar XP is a percent of it)
     * @param item     item of a custom ore (null = vanilla drops)
     * @param rarity   rarity of a custom ore's item (lowercase id)
     */
    record Ore(String id, String block, int tier, double depthsXp, String item, String rarity) {

        boolean keep() {
            return KEEP.equals(block);
        }

        boolean custom() {
            return item != null;
        }

        /** Counts as an ore (pillar XP, gather hook): anything but the kept block. */
        boolean ore() {
            return !keep() && tier > 0;
        }
    }

    /** One level: ores with cumulative weights. */
    record Level(int level, Ore[] ores, double[] cumulative) {

        double total() {
            return cumulative.length == 0 ? 0 : cumulative[cumulative.length - 1];
        }
    }

    private final Map<String, Ore> ores;
    private final TreeMap<Integer, Level> levels;

    private GeneratorTable(Map<String, Ore> ores, TreeMap<Integer, Level> levels) {
        this.ores = ores;
        this.levels = levels;
    }

    static GeneratorTable parse(List<String> oreLines, List<String> levelLines, Consumer<String> problem) {
        Map<String, Ore> ores = new LinkedHashMap<>();
        for (String line : oreLines == null ? List.<String>of() : oreLines) {
            Entry e = entry(line);
            if (e == null || e.args.isEmpty()) {
                problem.accept("generators.yml → ores: '" + line + "' is not '<id>: block=<block> ...' (skipped)");
                continue;
            }
            if (ores.containsKey(e.id)) {
                problem.accept("generators.yml → ores: '" + e.id + "' is listed twice (the first one is used)");
                continue;
            }
            String block = e.args.getOrDefault("block", "").toLowerCase(Locale.ROOT).replace("minecraft:", "");
            if (block.isEmpty()) {
                problem.accept("generators.yml → ores." + e.id + ": block= is missing (skipped)");
                continue;
            }
            int tier = (int) number(e.args.get("tier"), 0, "ores." + e.id + ".tier", problem);
            double xp = number(e.args.get("depths-xp"), 0, "ores." + e.id + ".depths-xp", problem);
            String item = e.args.get("item");
            item = item == null || item.isBlank() ? null : item.toLowerCase(Locale.ROOT).replace("minecraft:", "");
            if (item != null && KEEP.equals(block)) {
                problem.accept("generators.yml → ores." + e.id + ": a custom ore needs its own block, not keep (item ignored)");
                item = null;
            }
            String rarity = e.args.getOrDefault("rarity", "common").toLowerCase(Locale.ROOT);
            ores.put(e.id, new Ore(e.id, block, Math.max(0, tier), Math.max(0, xp), item, rarity));
        }
        if (ores.values().stream().noneMatch(Ore::keep)) {
            ores.put("cobble", new Ore("cobble", KEEP, 0, 0, null, "common"));
        }
        TreeMap<Integer, Level> levels = new TreeMap<>();
        for (String line : levelLines == null ? List.<String>of() : levelLines) {
            Entry e = entry(line);
            Integer level = e == null ? null : integer(e.id);
            if (level == null || level < 0) {
                problem.accept("generators.yml → levels: '" + line + "' is not '<level>: <ore>=<weight> ...' (skipped)");
                continue;
            }
            if (levels.containsKey(level)) {
                problem.accept("generators.yml → levels: level " + level + " is listed twice (the first one is used)");
                continue;
            }
            List<Ore> picked = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            for (Map.Entry<String, String> w : e.args.entrySet()) {
                Ore ore = ores.get(w.getKey());
                double weight = number(w.getValue(), -1, "levels." + level + "." + w.getKey(), problem);
                if (ore == null) {
                    problem.accept("generators.yml → levels." + level + ": unknown ore '" + w.getKey() + "' (skipped)");
                } else if (weight > 0) {
                    picked.add(ore);
                    weights.add(weight);
                }
            }
            if (picked.isEmpty()) {
                problem.accept("generators.yml → levels." + level + ": no ore with a weight above 0 (skipped)");
                continue;
            }
            double[] cumulative = new double[picked.size()];
            double sum = 0;
            for (int i = 0; i < cumulative.length; i++) {
                sum += weights.get(i);
                cumulative[i] = sum;
            }
            levels.put(level, new Level(level, picked.toArray(Ore[]::new), cumulative));
        }
        return new GeneratorTable(Collections.unmodifiableMap(ores), levels);
    }

    /** Every ore by id, in file order. */
    Map<String, Ore> ores() {
        return ores;
    }

    /** The kept block entry (cobble). */
    Ore keep() {
        for (Ore o : ores.values()) {
            if (o.keep()) {
                return o;
            }
        }
        throw new IllegalStateException("parse always adds a keep entry");
    }

    /** The highest listed level (0 when none). */
    int maxLevel() {
        return levels.isEmpty() ? 0 : levels.lastKey();
    }

    /** The level that applies to an island at {@code level}, or null (plain cobble). */
    Level levelFor(int level) {
        Map.Entry<Integer, Level> e = levels.floorEntry(level);
        return e == null ? null : e.getValue();
    }

    /** True when an island at this level can form anything but the kept block (worth a roll at all). */
    boolean rolls(int level) {
        Level l = levelFor(level);
        if (l == null) {
            return false;
        }
        for (Ore o : l.ores()) {
            if (!o.keep()) {
                return true;
            }
        }
        return false;
    }

    /** Picks the ore for an island at {@code level} with {@code random} in [0, 1). */
    Ore roll(int level, double random) {
        Level l = levelFor(level);
        if (l == null) {
            return keep();
        }
        double target = Math.min(Math.max(random, 0), Math.nextDown(1.0)) * l.total();
        double[] c = l.cumulative();
        for (int i = 0; i < c.length; i++) {
            if (target < c[i]) {
                return l.ores()[i];
            }
        }
        return l.ores()[c.length - 1];
    }

    /** The chance (0..1) of an ore at a level, for tests and lore. */
    double chance(int level, String oreId) {
        Level l = levelFor(level);
        if (l == null) {
            return keep().id().equals(oreId) ? 1 : 0;
        }
        double prev = 0;
        double out = 0;
        for (int i = 0; i < l.ores().length; i++) {
            if (l.ores()[i].id().equals(oreId)) {
                out += l.cumulative()[i] - prev;
            }
            prev = l.cumulative()[i];
        }
        return out / l.total();
    }

    // ------------------------------------------------------------------ parsing helpers

    private record Entry(String id, Map<String, String> args) {
    }

    /** {@code "<id>: k=v k=v"} → id + args (lowercase keys); null when there is no id. */
    private static Entry entry(String line) {
        if (line == null) {
            return null;
        }
        int colon = line.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String id = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            return null;
        }
        Map<String, String> args = new LinkedHashMap<>();
        for (String part : line.substring(colon + 1).trim().split("\\s+")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                args.putIfAbsent(part.substring(0, eq).trim().toLowerCase(Locale.ROOT), part.substring(eq + 1).trim());
            }
        }
        return new Entry(id, args);
    }

    private static Integer integer(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double number(String s, double fallback, String where, Consumer<String> problem) {
        if (s == null) {
            return fallback;
        }
        try {
            double d = Double.parseDouble(s.trim());
            if (Double.isFinite(d)) {
                return d;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        problem.accept("generators.yml → " + where + ": '" + s + "' is not a number");
        return fallback;
    }
}

package net.skybornmc.skyblock.production;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * The crop tiers ({@code crops.yml → crops}, PHASE-7 M7.3, WORLD §5.1), parsed without the server so it is unit-tested.
 * Block and item names stay strings here; the module resolves them to materials.
 */
final class CropCatalog {

    /** How a crop counts as ripe. */
    enum Kind {
        /** Ripe at full age; the seed places the harvested block (wheat, carrots, cocoa, nether wart …). */
        AGE,
        /** A column that grows upwards (sugar cane, cactus): only grown blocks count. */
        STACK,
        /** A block that grows from a plant (melon, pumpkin from stems, torchflower from its crop): only grown blocks count. */
        FRUIT
    }

    /**
     * One crop.
     *
     * @param block harvested block id ({@code carrots}, {@code melon})
     * @param plant block the seed places ({@code carrots}, {@code melon_stem}); the planting gate checks it
     * @param item  the crop item (Harvest Yield copies it)
     */
    record Crop(String id, int tier, int level, double xp, String block, String plant, String item, Kind kind) {
    }

    private final Map<String, Crop> byBlock;
    private final Map<String, Crop> byPlant;
    private final List<Crop> crops;

    private CropCatalog(List<Crop> crops) {
        this.crops = List.copyOf(crops);
        Map<String, Crop> blocks = new LinkedHashMap<>();
        Map<String, Crop> plants = new LinkedHashMap<>();
        for (Crop c : crops) {
            blocks.put(c.block(), c);
            plants.putIfAbsent(c.plant(), c);
        }
        this.byBlock = Collections.unmodifiableMap(blocks);
        this.byPlant = Collections.unmodifiableMap(plants);
    }

    static CropCatalog parse(List<String> lines, Consumer<String> problem) {
        List<Crop> out = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        java.util.Set<String> blocks = new java.util.HashSet<>();
        for (String line : lines == null ? List.<String>of() : lines) {
            int colon = line == null ? -1 : line.indexOf(':');
            String id = colon <= 0 ? "" : line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                problem.accept("crops.yml → crops: '" + line + "' is not '<id>: tier=<n> level=<n> xp=<n> block=<block> item=<item>' (skipped)");
                continue;
            }
            Map<String, String> args = new LinkedHashMap<>();
            for (String part : line.substring(colon + 1).trim().split("\\s+")) {
                int eq = part.indexOf('=');
                if (eq > 0) {
                    args.putIfAbsent(part.substring(0, eq).trim().toLowerCase(Locale.ROOT), part.substring(eq + 1).trim());
                }
            }
            String where = "crops.yml → crops." + id;
            if (!ids.add(id)) {
                problem.accept(where + ": listed twice (the first one is used)");
                continue;
            }
            String block = name(args.get("block"));
            String item = name(args.get("item"));
            if (block == null || item == null) {
                problem.accept(where + ": block= and item= are needed (skipped)");
                continue;
            }
            if (!blocks.add(block)) {
                problem.accept(where + ": block " + block + " belongs to another crop (skipped)");
                continue;
            }
            String plant = name(args.get("plant"));
            Kind kind = kind(args.get("kind"));
            if (kind == null) {
                problem.accept(where + ": kind must be age, stack or fruit (using age)");
                kind = Kind.AGE;
            }
            if (kind == Kind.FRUIT && plant == null) {
                problem.accept(where + ": a fruit crop needs plant= (the block that grows it) (skipped)");
                continue;
            }
            int tier = (int) number(args.get("tier"), 1, where + ".tier", problem);
            int level = (int) number(args.get("level"), 0, where + ".level", problem);
            double xp = number(args.get("xp"), 0, where + ".xp", problem);
            out.add(new Crop(id, Math.max(1, tier), Math.max(0, level), Math.max(0, xp), block, plant == null ? block : plant, item, kind));
        }
        return new CropCatalog(out);
    }

    private static String name(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
    }

    private static Kind kind(String raw) {
        if (raw == null || raw.isBlank()) {
            return Kind.AGE;
        }
        try {
            return Kind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
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
        problem.accept(where + ": '" + s + "' is not a number");
        return fallback;
    }

    List<Crop> crops() {
        return crops;
    }

    /** The crop harvested from this block id, or null. */
    Crop byBlock(String blockId) {
        return byBlock.get(blockId);
    }

    /** The crop whose seed places this block id, or null. */
    Crop byPlant(String blockId) {
        return byPlant.get(blockId);
    }

    /** Farming level needed to plant this block id (0 = no crop, no gate). */
    int plantLevel(String blockId) {
        Crop c = byPlant.get(blockId);
        return c == null ? 0 : c.level();
    }

    /**
     * The Farming level that opens each tier (tier 1, 2, 3 …), ascending: the lowest level of the crops in that tier. Tiers
     * without a crop are left out.
     */
    List<Integer> tierLevels() {
        Map<Integer, Integer> lowest = new java.util.TreeMap<>();
        for (Crop c : crops) {
            lowest.merge(c.tier(), c.level(), Math::min);
        }
        return List.copyOf(new TreeSet<>(lowest.values()));
    }
}

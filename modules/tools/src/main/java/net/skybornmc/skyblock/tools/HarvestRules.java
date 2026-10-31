package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.tools.ToolKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Pure rules of the harvester hoe (PHASE-7 M7.4, owner V6 / PD13), unit-tested: the area size by tool level, the area
 * offsets, which crops in the area are harvested (ripe only, replant gated by the Farming level), hoe tool XP per crop and
 * the hoe's extra crop drops.
 *
 * <p>Crop shapes ({@code tools.yml → harvester.*}):</p>
 * <ul>
 *   <li>{@link Shape#AGE}: wheat, carrots, potatoes, beetroots, nether wart, cocoa. Ripe at full age; broken and
 *       replanted at age 0 from one of its own seeds.</li>
 *   <li>{@link Shape#COLUMN}: sugar cane, cactus. Ripe when the column is two or more blocks high; the block above the
 *       bottom one is broken, so the bottom block stays and grows again (no replanting needed).</li>
 *   <li>{@link Shape#FRUIT}: melon, pumpkin. Only fruit blocks an attached stem points at (never placed decoration);
 *       the stem stays and grows a new fruit.</li>
 * </ul>
 * Sweet berries, torchflowers and pitcher plants are not area-harvested (vanilla picking / breaking as usual).
 */
final class HarvestRules {

    private HarvestRules() {
    }

    enum Shape { AGE, COLUMN, FRUIT }

    /**
     * One crop the harvester knows.
     *
     * @param block the crop block id ({@code wheat}, {@code sugar_cane}, {@code melon})
     * @param shape how it is harvested
     * @param extra AGE: the seed item id it is replanted from; FRUIT: the attached stem block id; COLUMN: empty
     */
    record Crop(String block, Shape shape, String extra) {
    }

    /** What the harvester does with one block of the area. */
    enum Action { SKIP, HARVEST, HARVEST_REPLANT }

    /**
     * The facts about one area block the decision needs (read from the world by {@link Harvester}).
     *
     * @param age      AGE: current age
     * @param maxAge   AGE: maximum age
     * @param height   COLUMN: blocks in the column from its bottom block up
     * @param attached FRUIT: an attached stem points at this block
     */
    record Cell(int age, int maxAge, int height, boolean attached) {
        static Cell age(int age, int maxAge) {
            return new Cell(age, maxAge, 0, false);
        }

        static Cell column(int height) {
            return new Cell(0, 0, height, false);
        }

        static Cell fruit(boolean attached) {
            return new Cell(0, 0, 0, attached);
        }
    }

    // ------------------------------------------------------------------ area

    /**
     * Side length of the square area: 1 + 2 per reached size level ({@code 1, 15, 30} → 3×3 from Lv 1, 5×5 at 15, 7×7 at
     * 30), plus 2 per extra ring (Wide Reap, E6), never above {@code maxSize} (odd, at least 1).
     */
    static int size(int level, List<Integer> sizeLevels, int extraRings, int maxSize) {
        int rings = 0;
        for (int at : sizeLevels) {
            if (level >= at) {
                rings++;
            }
        }
        int size = 1 + 2 * (rings + Math.max(0, extraRings));
        int max = Math.max(1, maxSize % 2 == 0 ? maxSize - 1 : maxSize);
        return Math.min(size, max);
    }

    /** Offsets {dx, dz} of a size×size square around the centre, the centre first, each exactly once. */
    static List<int[]> offsets(int size) {
        int r = Math.max(0, (size - 1) / 2);
        List<int[]> out = new ArrayList<>((2 * r + 1) * (2 * r + 1));
        out.add(new int[]{0, 0});
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx != 0 || dz != 0) {
                    out.add(new int[]{dx, dz});
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ decision

    /**
     * What happens to one area block: only ripe crops are touched; an AGE crop is replanted only when the player's Farming
     * level may plant it (PD10), otherwise it is left alone (the player can still break it by hand).
     *
     * @param plantLevel   Farming level needed to plant this crop (0 = no gate)
     * @param farmingLevel the player's Farming level
     */
    static Action decide(Crop crop, Cell cell, int plantLevel, int farmingLevel) {
        if (crop == null || cell == null) {
            return Action.SKIP;
        }
        return switch (crop.shape()) {
            case AGE -> cell.maxAge() > 0 && cell.age() >= cell.maxAge() && (plantLevel <= 0 || farmingLevel >= plantLevel)
                    ? Action.HARVEST_REPLANT : Action.SKIP;
            case COLUMN -> cell.height() >= 2 ? Action.HARVEST : Action.SKIP;
            case FRUIT -> cell.attached() ? Action.HARVEST : Action.SKIP;
        };
    }

    /** Age a replanted crop starts at: 0 plus the Green Thumb stages (E6 hook), never fully grown. */
    static int replantAge(int bonusStages, int maxAge) {
        if (maxAge <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(maxAge - 1, bonusStages));
    }

    // ------------------------------------------------------------------ XP and drops

    /**
     * Hoe tool XP for one {@code IslandCropHarvestEvent} (the only hoe XP source): PD11-eligible harvests (a hoe, no bone
     * meal, a member on their own island, ripe) with a custom hoe in hand; {@code tierXp} of the crop tier × crops ×
     * {@code factor}. Every crop fires its own event once, so an area swing pays each crop once.
     */
    static double toolXp(boolean xpEligible, ToolKind heldKind, double tierXp, int crops, double factor) {
        if (!xpEligible || heldKind != ToolKind.HOE || crops <= 0 || !(tierXp > 0) || !(factor > 0)
                || !Double.isFinite(tierXp) || !Double.isFinite(factor)) {
            return 0;
        }
        return tierXp * crops * factor;
    }

    /**
     * New amount of a dropped crop stack with the hoe's drop multiplier ({@code 1.25} = +25%: a quarter extra on average;
     * the whole part is sure, the rest is a chance). Capped at {@code maxMultiplier} (the "max 3 drops per action" rule) and
     * the stack size. {@code roll} is uniform in [0, 1).
     */
    static int boostedAmount(int amount, double multiplier, double maxMultiplier, int maxStack, double roll) {
        if (amount <= 0) {
            return Math.max(0, amount);
        }
        double m = Double.isFinite(multiplier) ? Math.max(1, Math.min(Math.max(1, maxMultiplier), multiplier)) : 1;
        double expected = amount * m;
        long whole = (long) Math.floor(expected);
        long out = roll < expected - whole ? whole + 1 : whole;
        return (int) Math.max(amount, Math.min(Math.max(amount, maxStack), out));
    }

    // ------------------------------------------------------------------ config parsing

    /**
     * The crop list from the three config lists: {@code replant} ("block=seed"), {@code columns} ("block") and
     * {@code fruits} ("block=stem"). Broken lines are reported and skipped; a block listed twice keeps the first.
     */
    static Map<String, Crop> crops(List<String> replant, List<String> columns, List<String> fruits, Consumer<String> problems) {
        Map<String, Crop> out = new LinkedHashMap<>();
        for (String line : replant) {
            String[] kv = pair(line);
            if (kv == null) {
                problems.accept("tools.yml harvester.replant: '" + line + "' must be 'crop_block=seed_item'; skipped");
                continue;
            }
            out.putIfAbsent(kv[0], new Crop(kv[0], Shape.AGE, kv[1]));
        }
        for (String line : columns) {
            String id = clean(line);
            if (!ToolCatalog.validId(id)) {
                problems.accept("tools.yml harvester.columns: '" + line + "' must be a block id; skipped");
                continue;
            }
            out.putIfAbsent(id, new Crop(id, Shape.COLUMN, ""));
        }
        for (String line : fruits) {
            String[] kv = pair(line);
            if (kv == null) {
                problems.accept("tools.yml harvester.fruits: '" + line + "' must be 'fruit_block=attached_stem_block'; skipped");
                continue;
            }
            out.putIfAbsent(kv[0], new Crop(kv[0], Shape.FRUIT, kv[1]));
        }
        return Collections.unmodifiableMap(out);
    }

    private static String[] pair(String line) {
        if (line == null) {
            return null;
        }
        int eq = line.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        String a = clean(line.substring(0, eq));
        String b = clean(line.substring(eq + 1));
        return ToolCatalog.validId(a) && ToolCatalog.validId(b) ? new String[]{a, b} : null;
    }

    private static String clean(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
    }

    /** Ascending positive ints from a list of strings (broken entries dropped). */
    static List<Integer> levels(List<String> raw) {
        List<Integer> out = new ArrayList<>();
        for (String s : raw) {
            try {
                int v = Integer.parseInt(s.trim());
                if (v >= 1 && !out.contains(v)) {
                    out.add(v);
                }
            } catch (NumberFormatException | NullPointerException ignored) {
                // skipped
            }
        }
        out.sort(Integer::compare);
        return List.copyOf(out);
    }
}

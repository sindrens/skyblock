package net.skybornmc.skyblock.islandtools;

import java.util.List;
import java.util.Locale;

/** Pure rules of the island tools (unit-tested). */
final class ToolRules {

    /** T3: the tier ladder. */
    static final List<String> TIERS = List.of("wood", "stone", "iron", "gold", "diamond", "netherite");

    private ToolRules() {
    }

    /** The vanilla material id of a tool: {@code wooden_pickaxe}, {@code golden_hoe} ... */
    static String material(Kind kind, int tier) {
        String t = TIERS.get(Math.max(0, Math.min(TIERS.size() - 1, tier)));
        String prefix = switch (t) {
            case "wood" -> "wooden";
            case "gold" -> "golden";
            default -> t;
        };
        return (prefix + "_" + kind.id()).toLowerCase(Locale.ROOT);
    }

    /** T9: hoe row length per Row level (level 0 = 1 block; 1-4 = 3, 5, 7, 9 with the default list). */
    static int row(int level, List<Integer> lengths) {
        if (lengths.isEmpty()) {
            return 1;
        }
        return lengths.get(Math.max(0, Math.min(lengths.size() - 1, level)));
    }

    /** T9: hoe storage, 256 at level 0 and about x3 per step. */
    static long storage(int level, long base, double factor) {
        return Math.round(base * Math.pow(factor, Math.max(0, level)));
    }

    /**
     * T11/G11: regrow seconds. The island's crop-growth upgrade (value, percent) moves it from {@code base} (60) to
     * {@code top} (15) at {@code fullAt} percent; the CROP_GROWTH bonus family divides it further, never under {@code floor}.
     */
    static double regrowSeconds(double upgradePercent, double familyPercent, double base, double top, double fullAt, double floor) {
        double share = fullAt <= 0 ? 0 : Math.max(0, Math.min(1, upgradePercent / fullAt));
        double s = base - (base - top) * share;
        s = s / (1 + Math.max(0, familyPercent) / 100.0);
        return Math.max(floor, s);
    }

    /** T12: Growth Burst chance in percent from the island's crop-growth upgrade. */
    static double burstPercent(double upgradePercent, double perGrowthPercent, double max) {
        return Math.max(0, Math.min(max, upgradePercent * perGrowthPercent));
    }

    /** Random rounding of an expected amount (2.3 = 2, and 3 three times in ten). */
    static int roll(double expected, double random01) {
        int base = (int) Math.floor(expected);
        return base + (random01 < expected - base ? 1 : 0);
    }

    /** T8: no confirm for normal upgrades, one above the threshold. */
    static boolean needsConfirm(long cost, long threshold) {
        return threshold > 0 && cost >= threshold;
    }

    /** The positions of a hoe row: {@code length} blocks centred on (x, z), across the direction the player faces. */
    static int[][] rowOffsets(int length, float yaw) {
        int n = Math.max(1, length);
        int half = n / 2;
        // Facing north/south (yaw near 0 or 180): the row runs east-west; facing east/west: north-south.
        double a = ((yaw % 360) + 360) % 360;
        boolean alongX = (a < 45 || a >= 315) || (a >= 135 && a < 225);
        int[][] out = new int[n][2];
        for (int i = 0; i < n; i++) {
            int d = i - half;
            out[i] = alongX ? new int[]{d, 0} : new int[]{0, d};
        }
        return out;
    }
}

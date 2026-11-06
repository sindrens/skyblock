package net.skybornmc.skyblock.islandtools;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure rules of the island tools (unit-tested). */
final class ToolRules {

    private ToolRules() {
    }

    /** Owner 4 Nov 2026: both island tools are diamond from the start (no wood to netherite tiers any more). */
    static String material(Kind kind) {
        if (kind == Kind.ROD) {
            return "fishing_rod"; // there is no diamond rod
        }
        return ("diamond_" + kind.id()).toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ levels and prestige (sawtooth)

    /**
     * XP needed to go from {@code level} to {@code level + 1}: {@code base × level^exponent × (1 + factor × prestige)}
     * (owner 4 Nov 2026), at least 1.
     */
    static long xpNeed(int level, int prestige, double base, double exponent, double factor) {
        double v = base * Math.pow(Math.max(1, level), exponent) * (1 + Math.max(0, factor) * Math.max(0, prestige));
        return Math.max(1, Math.round(v));
    }

    /** A tool's level from its XP in this prestige: {@code level} (1..max), XP into it and XP for the next level (0 at max). */
    record Level(int level, long into, long need) {

        boolean maxed() {
            return need == 0;
        }

        /** 0..1 towards the next level (1 at max). */
        double progress() {
            return need == 0 ? 1 : Math.min(1, (double) into / need);
        }
    }

    static Level levelOf(long xp, int prestige, int max, double base, double exponent, double factor) {
        int level = 1;
        long left = Math.max(0, xp);
        while (level < max) {
            long need = xpNeed(level, prestige, base, exponent, factor);
            if (left < need) {
                return new Level(level, left, need);
            }
            left -= need;
            level++;
        }
        return new Level(Math.max(1, max), 0, 0);
    }

    /** XP from level 1 to {@code max} in this prestige (what a prestige needs). */
    static long xpToMax(int prestige, int max, double base, double exponent, double factor) {
        long sum = 0;
        for (int l = 1; l < max; l++) {
            sum += xpNeed(l, prestige, base, exponent, factor);
        }
        return sum;
    }

    /** Owner 4 Nov 2026: ★ per prestige up to five (★★★★★), then "★6", "★7" ... Empty at prestige 0. */
    static String stars(int prestige, int maxStars) {
        if (prestige <= 0) {
            return "";
        }
        return prestige <= maxStars ? "★".repeat(prestige) : "★" + prestige;
    }

    /** The permanent prestige bonus multiplier (+{@code perPrestige}% per prestige): XP and shards of the tool. */
    static double prestigeBonus(int prestige, double perPrestigePercent) {
        return 1 + Math.max(0, prestige) * Math.max(0, perPrestigePercent) / 100.0;
    }

    /** The upgrade price factor (-{@code perPrestige}% per prestige, never under {@code floorPercent}). */
    static double priceFactor(int prestige, double perPrestigePercent, double floorPercent) {
        double f = 1 - Math.max(0, prestige) * Math.max(0, perPrestigePercent) / 100.0;
        return Math.max(Math.max(0, floorPercent) / 100.0, f);
    }

    // ------------------------------------------------------------------ Sweep / Vein bands

    /** One prestige band: from {@code prestige} on, up to {@code blocks} extra blocks, {@code maxChance}% at the top level. */
    record Band(int prestige, int blocks, double maxChance) {
    }

    /** Parses {@code prestige:blocks:max-chance} lines (broken ones skipped), sorted by prestige. */
    static List<Band> bands(List<String> lines) {
        List<Band> out = new java.util.ArrayList<>();
        for (String line : lines == null ? List.<String>of() : lines) {
            String[] p = line.trim().split(":");
            if (p.length != 3) {
                continue;
            }
            try {
                int pr = Integer.parseInt(p[0].trim());
                int blocks = Integer.parseInt(p[1].trim());
                double chance = Double.parseDouble(p[2].trim());
                if (pr >= 0 && blocks > 0 && chance >= 0 && chance <= 100) {
                    out.add(new Band(pr, blocks, chance));
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        out.sort(java.util.Comparator.comparingInt(Band::prestige));
        return out;
    }

    /** The band of a prestige (the highest band at or below it), or null below the first band (locked). */
    static Band band(List<Band> bands, int prestige) {
        Band out = null;
        for (Band b : bands) {
            if (b.prestige() <= prestige) {
                out = b;
            }
        }
        return out;
    }

    /** Sweep/Vein chance in percent: the band's top chance × level / max. */
    static double sweepChance(Band band, int level, int max) {
        if (band == null || max <= 0 || level <= 0) {
            return 0;
        }
        return band.maxChance() * Math.min(level, max) / (double) max;
    }

    // ------------------------------------------------------------------ migration (4 Nov 2026 rework)

    /**
     * The levels of the old upgrade set (v6 defaults: hoe extra drops / shard finder / storage 10, pickaxe 5 ...), used to
     * carry bought levels over: {@code old n → floor(n × newMax / oldMax)}.
     */
    static final Map<String, Integer> OLD_MAX = Map.ofEntries(
            Map.entry("hoe:extra_drops", 10), Map.entry("hoe:shard_finder", 10), Map.entry("hoe:storage", 10),
            Map.entry("hoe:efficiency", 5), Map.entry("hoe:fortune", 3), Map.entry("hoe:haste", 2), Map.entry("hoe:auto_sell", 1),
            Map.entry("pickaxe:efficiency", 5), Map.entry("pickaxe:fortune", 3), Map.entry("pickaxe:haste", 2),
            Map.entry("pickaxe:smelter", 5), Map.entry("pickaxe:extra_drops", 5), Map.entry("pickaxe:shard_finder", 5),
            Map.entry("pickaxe:auto_sell", 1));

    /** {@code floor(old × newMax / oldMax)}, within 0..newMax. */
    static int carryOver(int oldLevel, int oldMax, int newMax) {
        if (oldLevel <= 0 || oldMax <= 0 || newMax <= 0) {
            return 0;
        }
        return (int) Math.min(newMax, (long) oldLevel * newMax / oldMax);
    }

    /**
     * The old island levels of one tool ({@code a=3,b=1}) in the new upgrade set: known upgrades carried over
     * proportionally, removed ones (row, magnet, experience ...) dropped.
     */
    static Map<String, Integer> migrate(Kind kind, String oldUpgrades, Upgrades now) {
        Map<String, Integer> out = new java.util.TreeMap<>();
        Upgrades.decode(oldUpgrades).forEach((id, lv) -> {
            Integer oldMax = OLD_MAX.get(kind.id() + ":" + id);
            now.get(kind, id).ifPresent(def -> {
                int n = carryOver(lv, oldMax == null ? def.max() : oldMax, def.max());
                if (n > 0) {
                    out.put(id, n);
                }
            });
        });
        return out;
    }

    // ------------------------------------------------------------------ island crops

    /** Hoe storage: {@code base + level × perLevel} items. */
    static long storage(int level, long base, long perLevel) {
        return Math.max(0, base) + (long) Math.max(0, level) * Math.max(0, perLevel);
    }

    /**
     * T11/G11: regrow seconds. The island's crop-growth upgrade (value, percent) moves it from {@code base} (60) to
     * {@code top} (15) at {@code fullAt} percent; the CROP_GROWTH bonus family (and the hoe's Growth) divides it further,
     * never under {@code floor}.
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
        int base = (int) Math.floor(Math.max(0, expected));
        return base + (random01 < expected - base ? 1 : 0);
    }

    /** The buy amount of a click: plain x1, shift x10, right x100 (owner 4 Nov 2026). */
    static int buyAmount(boolean shift, boolean right) {
        return shift ? 10 : right ? 100 : 1;
    }
}

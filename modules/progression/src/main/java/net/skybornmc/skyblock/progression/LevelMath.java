package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.LevelSource;

import java.util.Collection;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The island level formula (PHASE-5 M5.2), free of Bukkit so every rule is unit-tested: points per source, the caps and
 * the level. Island Level = floor(total / divisor).
 *
 * <p><b>Caps:</b> a capped source can't be more than its share of the total: each capped source is limited to
 * {@code cap / (1 - cap) x (sum of the other sources)}, all caps holding together ({@link #applyCaps}).</p>
 */
final class LevelMath {

    private LevelMath() {
    }

    /** The weights of {@code progression.yml}. */
    record Weights(double divisor, double perPillarLevel, double skillsDivisor, double perDepthsTier, double bankFactor,
                   long bankBase, double blocksFactor, double blocksUnit, Map<LevelSource, Double> capPercents) {

        static Weights of(LevelConfig c) {
            Map<LevelSource, Double> caps = new EnumMap<>(LevelSource.class);
            caps.put(LevelSource.ACHIEVEMENTS, c.capAchievements);
            caps.put(LevelSource.COLLECTIONS, c.capCollections);
            caps.put(LevelSource.SKILLS, c.capSkills);
            caps.put(LevelSource.DEPTHS, c.capDepths);
            caps.put(LevelSource.BANK, c.capBank);
            caps.put(LevelSource.BLOCKS, c.capBlocks);
            return new Weights(c.divisor, c.perPillarLevel, c.skillsDivisor, c.perDepthsTier, c.bankFactor, c.bankBase,
                    c.blocksFactor, c.blocksUnit, caps);
        }
    }

    /**
     * What the level is made of right now.
     *
     * @param pillarLevels      sum of the shown pillars' levels
     * @param upgrades          raw points of upgrades, masteries and Ascension (provider; 0 until M5.4/M5.5)
     * @param achievements      island achievement points (provider)
     * @param collections       collection milestones (provider)
     * @param memberSkillTotals total skill level of every COUNTED member (3+ days, never coop)
     * @param depthsTiers       best counted member's unlocked tiers, summed over the aspects
     * @param bankLowest        lowest island bank balance of the window (0 while not observed long enough)
     * @param blockWorth        money worth of the placed blocks inside the border
     */
    record Inputs(int pillarLevels, double upgrades, double achievements, double collections, List<Integer> memberSkillTotals,
                  int depthsTiers, long bankLowest, double blockWorth) {
    }

    /** One calculated level. {@code raw} = before the caps, {@code points} = after; {@code limits} = cap limit per capped source. */
    record Result(Map<LevelSource, Double> raw, Map<LevelSource, Double> points, Map<LevelSource, Double> limits,
                  double total, double earned, int level, double progress) {
    }

    static Result calculate(Inputs in, Weights w) {
        Map<LevelSource, Double> raw = raw(in, w);
        Map<LevelSource, Double> limits = new EnumMap<>(LevelSource.class);
        Map<LevelSource, Double> points = applyCaps(raw, w.capPercents(), limits);
        double total = total(points.values());
        double earned = 0;
        for (Map.Entry<LevelSource, Double> e : points.entrySet()) {
            if (e.getKey().earned()) {
                earned += e.getValue();
            }
        }
        return new Result(raw, points, limits, total, earned, level(total, w.divisor()), progress(total, w.divisor()));
    }

    static Map<LevelSource, Double> raw(Inputs in, Weights w) {
        Map<LevelSource, Double> out = new EnumMap<>(LevelSource.class);
        out.put(LevelSource.PILLARS, positive(Math.max(0, in.pillarLevels()) * w.perPillarLevel()));
        out.put(LevelSource.UPGRADES, positive(in.upgrades()));
        out.put(LevelSource.ACHIEVEMENTS, positive(in.achievements()));
        out.put(LevelSource.COLLECTIONS, positive(in.collections()));
        out.put(LevelSource.SKILLS, skillsPoints(in.memberSkillTotals(), w.skillsDivisor()));
        out.put(LevelSource.DEPTHS, positive(Math.max(0, in.depthsTiers()) * w.perDepthsTier()));
        out.put(LevelSource.BANK, bankPoints(in.bankLowest(), w.bankFactor(), w.bankBase()));
        out.put(LevelSource.BLOCKS, blockPoints(in.blockWorth(), w.blocksFactor(), w.blocksUnit()));
        return out;
    }

    /** Average total skill level of the counted members / divisor (alts lower the average); 0 without counted members. */
    static double skillsPoints(Collection<Integer> totals, double divisor) {
        if (totals == null || totals.isEmpty() || !(divisor > 0)) {
            return 0;
        }
        double sum = 0;
        for (Integer t : totals) {
            sum += t == null ? 0 : Math.max(0, t);
        }
        return positive(sum / totals.size() / divisor);
    }

    /** {@code factor x log10(lowest / base)}, never below 0 (money can't buy the level: slow scale, lowest balance). */
    static double bankPoints(long lowest, double factor, long base) {
        if (lowest <= 0 || base <= 0 || lowest <= base) {
            return 0;
        }
        return positive(factor * Math.log10((double) lowest / base));
    }

    /** {@code factor x sqrt(worth / unit)}: twice the blocks is not twice the points. */
    static double blockPoints(double worth, double factor, double unit) {
        if (!(worth > 0) || !(unit > 0)) {
            return 0;
        }
        return positive(factor * Math.sqrt(worth / unit));
    }

    /**
     * Applies the caps so that every capped source is at most its share of the <b>final</b> total (all caps hold at the
     * same time). Exact: sources over their share are fixed at {@code share x T} one round at a time and the total
     * {@code T = (uncapped points) / (1 - sum of the fixed shares)} is solved again, until no other source is over its
     * share (the total only goes down, so this ends after at most one round per capped source). At the end each capped
     * source equals {@code cap / (1 - cap) x (sum of the other sources)} or is below it, as the plan describes; solving
     * them together keeps a small cap from being measured against another source that is still uncapped.
     * {@code capPercents} 0 or missing = no cap; the caps must add up to less than 100%.
     *
     * @param limits receives the limit of every capped source (for "Blocks 39 (cap 86)" in the menu)
     */
    static Map<LevelSource, Double> applyCaps(Map<LevelSource, Double> raw, Map<LevelSource, Double> capPercents,
                                              Map<LevelSource, Double> limits) {
        Map<LevelSource, Double> out = new EnumMap<>(LevelSource.class);
        for (LevelSource s : LevelSource.values()) {
            out.put(s, positive(raw.getOrDefault(s, 0.0)));
        }
        Map<LevelSource, Double> shares = new EnumMap<>(LevelSource.class);
        double shareSum = 0;
        for (LevelSource s : LevelSource.values()) {
            double cap = capPercents == null ? 0 : capPercents.getOrDefault(s, 0.0);
            if (cap > 0 && cap < 100) {
                shares.put(s, cap / 100.0);
                shareSum += cap / 100.0;
            }
        }
        if (shares.isEmpty() || shareSum >= 1) {
            return out; // no caps (a broken cap sum is refused by the config limits; never divide by 0)
        }
        Set<LevelSource> fixed = EnumSet.noneOf(LevelSource.class);
        double total = total(out.values());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<LevelSource, Double> e : shares.entrySet()) {
                if (!fixed.contains(e.getKey()) && out.get(e.getKey()) > e.getValue() * total + 1e-12) {
                    fixed.add(e.getKey());
                    changed = true;
                }
            }
            if (changed) {
                double free = 0;
                double fixedShare = 0;
                for (LevelSource s : LevelSource.values()) {
                    if (fixed.contains(s)) {
                        fixedShare += shares.get(s);
                    } else {
                        free += out.get(s);
                    }
                }
                total = free / (1 - fixedShare);
            }
        }
        for (LevelSource s : fixed) {
            out.put(s, shares.get(s) * total);
        }
        double finalTotal = total(out.values());
        for (Map.Entry<LevelSource, Double> e : shares.entrySet()) {
            // the most this source could give next to the others as they are now (plan: "cap would be 86")
            limits.put(e.getKey(), capLimit(e.getValue(), finalTotal - out.get(e.getKey())));
        }
        return out;
    }

    /** Most a source with {@code share} (0.2 = 20%) may give next to {@code others} points: share / (1 - share) x others. */
    static double capLimit(double share, double others) {
        if (!(share > 0) || share >= 1) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0, share / (1 - share) * Math.max(0, others));
    }

    static int level(double total, double divisor) {
        if (!(divisor > 0) || !(total > 0)) {
            return 0;
        }
        // A tiny epsilon keeps 38.0 / 10 * 10 style float noise (e.g. 379.99999) from losing a level.
        double v = total / divisor + 1e-9;
        return v >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.floor(v);
    }

    /** Progress to the next level, 0.0 (inclusive) to 1.0 (exclusive). */
    static double progress(double total, double divisor) {
        if (!(divisor > 0) || !(total > 0)) {
            return 0;
        }
        double into = total - level(total, divisor) * divisor;
        return Math.max(0, Math.min(0.999999, into / divisor));
    }

    static double total(Collection<Double> values) {
        double sum = 0;
        for (Double v : values) {
            sum += v == null ? 0 : v;
        }
        return sum;
    }

    private static double positive(double v) {
        return Double.isFinite(v) && v > 0 ? v : 0;
    }
}

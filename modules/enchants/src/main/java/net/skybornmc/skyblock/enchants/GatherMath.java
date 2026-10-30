package net.skybornmc.skyblock.enchants;

import java.util.random.RandomGenerator;

/**
 * Numbers of the gathering enchants (ENCHANTS §6.1–§6.3, §8.1), pure and unit-tested: chance effects inside their bonus
 * family cap, random rounding of fractional yields, and the per-player budget of extra area blocks.
 */
final class GatherMath {

    private GatherMath() {
    }

    /**
     * A chance effect inside its family cap (EN16). {@code expectedPct} is what the effect adds to the family on average
     * at full chance (Geode Sense IV: 0.4% × 40 = +16% crystals). When the other sources already fill part of the family,
     * the chance is scaled so the effect adds at most what is left below the cap; a full family leaves 0.
     */
    static double cappedChance(double chancePct, double expectedPct, double familyTotal, double cap) {
        if (!(chancePct > 0)) {
            return 0;
        }
        if (!(expectedPct > 0) || !Double.isFinite(cap)) {
            return chancePct;
        }
        double room = Math.max(0, cap - Math.max(0, familyTotal));
        if (room >= expectedPct) {
            return chancePct;
        }
        return chancePct * room / expectedPct;
    }

    /** True with {@code percent} % probability. */
    static boolean roll(double percent, RandomGenerator random) {
        return percent > 0 && random.nextDouble() * 100 < percent;
    }

    /** {@code value} rounded down, plus one with the chance of the fraction (keeps the expected value exact). */
    static long randomRound(double value, RandomGenerator random) {
        if (!(value > 0) || !Double.isFinite(value)) {
            return 0;
        }
        long whole = (long) Math.floor(value);
        double fraction = value - whole;
        return fraction > 0 && random.nextDouble() < fraction ? whole + 1 : whole;
    }

    /**
     * Extra yield of {@code blocks} area blocks: the main block's rolled amount × yield percent × blocks, randomly rounded
     * (an unbiased estimate of rolling every block separately, without per-block rolls).
     */
    static long areaYield(long mainAmount, double yieldPercent, int blocks, RandomGenerator random) {
        if (mainAmount <= 0 || blocks <= 0 || !(yieldPercent > 0)) {
            return 0;
        }
        return randomRound(mainAmount * yieldPercent / 100.0 * blocks, random);
    }

    /** Actions an area proc adds for book drops: blocks × yield (§3.2: area blocks count at their yield). */
    static double areaActions(int blocks, double yieldPercent) {
        return blocks <= 0 || !(yieldPercent > 0) ? 0 : blocks * yieldPercent / 100.0;
    }

    /** Crystals of one Crystal Geode / Amber Resin: {@code worth} × the tier's average per block, at least {@code min}. */
    static long geodeCrystals(double perBlock, double worth, long min) {
        double v = Double.isFinite(perBlock) && Double.isFinite(worth) ? perBlock * worth : 0;
        return Math.max(Math.max(0, min), Math.round(Math.max(0, v)));
    }

    /** Counter that fires on every {@code every}-th action (Worldbreaker, Ashen Grove): the new counter value, negative when it fired. */
    static int everyNth(int counter, int every) {
        if (every <= 0) {
            return 0;
        }
        int next = Math.max(0, counter) + 1;
        return next >= every ? -1 : next;
    }

    /**
     * Budget of extra area blocks per player (a token bucket): refills {@code perSecond} tokens a second up to
     * {@code capacity}. Keeps area enchants from ever turning into a flood of block changes, whatever the procs roll.
     */
    static final class Budget {
        private double tokens;
        private long last;

        Budget(double capacity, long now) {
            this.tokens = capacity;
            this.last = now;
        }

        /** Takes up to {@code wanted} blocks; returns how many may be broken now. */
        int take(int wanted, long now, double perSecond, double capacity) {
            if (wanted <= 0) {
                return 0;
            }
            double elapsed = Math.max(0, now - last) / 1000.0;
            last = Math.max(last, now);
            tokens = Math.min(Math.max(1, capacity), tokens + elapsed * Math.max(0, perSecond));
            int granted = (int) Math.min(wanted, Math.floor(tokens));
            tokens -= granted;
            return Math.max(0, granted);
        }

        double tokens() {
            return tokens;
        }
    }
}

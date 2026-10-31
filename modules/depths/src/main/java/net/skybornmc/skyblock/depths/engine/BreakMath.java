package net.skybornmc.skyblock.depths.engine;

/**
 * Vanilla block-break timing, used to catch impossibly fast breaks.
 *
 * <p>Each tick a player adds {@code progressPerTick} to the break progress; the block breaks at 1.0. Vanilla's formula
 * is {@code toolSpeed / hardness / (canHarvest ? 30 : 100)}, where tool speed already includes Efficiency, Haste,
 * Mining Fatigue, water and airborne penalties. At runtime we read it from Paper ({@code Block#getBreakSpeed(Player)}),
 * which applies all of these; {@link #progressPerTick} is the same formula for tests and tools.</p>
 *
 * <p>Vanilla itself accepts a break once progress reaches 0.7 (latency), so the fastest legitimate break is about 70%
 * of the expected time. Anything below {@code minRatio × expected − grace} is flagged.</p>
 */
public final class BreakMath {

    /** Milliseconds per server tick at 20 TPS. */
    public static final long TICK_MILLIS = 50L;

    /** Returned by {@link #expectedTicks} for blocks that can't be broken (hardness &lt; 0 or no progress). */
    public static final int UNBREAKABLE = Integer.MAX_VALUE;

    private static final double EPSILON = 1e-4;

    private BreakMath() {
    }

    /**
     * Vanilla per-tick break progress.
     *
     * @param hardness   block hardness (negative = unbreakable, 0 = instant)
     * @param toolSpeed  destroy speed of the held item against the block, after enchantments and effects
     * @param canHarvest true if the block drops with this tool (or needs no correct tool)
     */
    public static float progressPerTick(float hardness, float toolSpeed, boolean canHarvest) {
        if (hardness < 0f) {
            return 0f;
        }
        if (hardness == 0f) {
            return 1f;
        }
        return toolSpeed / hardness / (canHarvest ? 30f : 100f);
    }

    /**
     * DV2-SPIKE hardness fix: the client times a break from the block it <b>sees</b> (a palette tier), the server only
     * knows the real block. Turns Paper's per-tick progress on the real block ({@code realSpeed}, which already holds
     * Efficiency, Haste, Mining Fatigue, water and airborne penalties) into the progress on the shown block by swapping
     * the block-dependent parts of the vanilla formula: tool speed, hardness and the harvest divisor (30 / 100).
     *
     * <p>When the real block is instant or unbreakable the effects can't be factored out; the plain formula for the
     * shown block is returned instead.</p>
     */
    public static float scaledSpeed(float realSpeed, float realHardness, float realToolSpeed, boolean realHarvest,
                                    float shownHardness, float shownToolSpeed, boolean shownHarvest) {
        if (shownHardness < 0f) {
            return 0f;
        }
        if (shownHardness == 0f) {
            return 1f;
        }
        if (!(realHardness > 0f) || !(realToolSpeed > 0f) || !(realSpeed > 0f)) {
            return progressPerTick(shownHardness, Math.max(shownToolSpeed, 0f), shownHarvest);
        }
        double effects = realSpeed * realHardness * (realHarvest ? 30.0 : 100.0) / realToolSpeed;
        return (float) (effects * shownToolSpeed / shownHardness / (shownHarvest ? 30.0 : 100.0));
    }

    /** Ticks needed to break: 0 = instant, {@link #UNBREAKABLE} = never. */
    public static int expectedTicks(float progressPerTick) {
        if (!(progressPerTick > 0f)) {
            return UNBREAKABLE;
        }
        if (progressPerTick >= 1f) {
            return 0;
        }
        double ticks = Math.ceil(1.0 / progressPerTick - EPSILON);
        return ticks >= UNBREAKABLE ? UNBREAKABLE : (int) ticks;
    }

    /** Expected break time in milliseconds (0 = instant, {@link Long#MAX_VALUE} = never). */
    public static long expectedMillis(int expectedTicks) {
        return expectedTicks == UNBREAKABLE ? Long.MAX_VALUE : expectedTicks * TICK_MILLIS;
    }

    /** Fastest break still accepted: {@code expected × minRatio − grace}, never below 0. */
    public static long minAllowedMillis(int expectedTicks, double minRatio, long graceMillis) {
        if (expectedTicks == UNBREAKABLE) {
            return Long.MAX_VALUE;
        }
        long min = Math.round(expectedTicks * TICK_MILLIS * minRatio) - graceMillis;
        return Math.max(0L, min);
    }

    /**
     * True if a break that took {@code actualMillis} is impossible. Instant (and one-tick) breaks are never flagged:
     * they need no start packet timing.
     */
    public static boolean tooFast(long actualMillis, int expectedTicks, double minRatio, long graceMillis) {
        if (expectedTicks <= 1) {
            return false;
        }
        return actualMillis < minAllowedMillis(expectedTicks, minRatio, graceMillis);
    }
}

package net.skybornmc.skyblock.pass;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tier math, overflow, catch-up and pouch sizes (P10-24, RX30, RX31). */
class PassMathTest {

    private final PassMath.Curve curve = new PassMath.Curve(100, 800, 2_000);

    @Test
    void flatLevels() {
        assertEquals(0, curve.tier(0));
        assertEquals(0, curve.tier(799));
        assertEquals(1, curve.tier(800));
        assertEquals(12, curve.tier(12 * 800 + 5));
        assertEquals(99, curve.tier(80_000 - 1));
        assertEquals(100, curve.tier(80_000));
        assertEquals(800, curve.xpFor(1));
        assertEquals(80_000, curve.xpFor(100));
        assertEquals(0, curve.xpFor(0));
    }

    @Test
    void overflowAfterTheLastLevel() {
        assertEquals(100, curve.tier(81_999));
        assertEquals(101, curve.tier(82_000));
        assertEquals(103, curve.tier(86_500));
        assertEquals(100, curve.level(86_500), "the shown level stops at the last level");
        assertEquals(82_000, curve.xpFor(101));
        assertArrayEquals(new long[]{500, 2_000}, curve.progress(86_500));
        assertArrayEquals(new long[]{300, 800}, curve.progress(1_100));
    }

    @Test
    void noOverflowStopsAtTheTop() {
        PassMath.Curve flat = new PassMath.Curve(100, 800, 0);
        assertEquals(100, flat.tier(1_000_000));
        assertEquals(Long.MAX_VALUE, flat.xpFor(101));
        assertArrayEquals(new long[]{800, 800}, flat.progress(1_000_000));
    }

    @Test
    void tierAndLevelAreConsistentWithXpFor() {
        for (int t = 0; t <= 130; t++) {
            assertEquals(t, curve.tier(curve.xpFor(t)), "tier " + t);
            if (t > 0) {
                assertEquals(t - 1, curve.tier(curve.xpFor(t) - 1), "just below tier " + t);
            }
        }
    }

    @Test
    void seasonDays() {
        LocalDate start = LocalDate.of(2026, 11, 1);
        assertEquals(0, PassMath.seasonDay(null, start, 90), "no start date");
        assertEquals(0, PassMath.seasonDay(start, start.minusDays(1), 90), "not started");
        assertEquals(1, PassMath.seasonDay(start, start, 90));
        assertEquals(90, PassMath.seasonDay(start, start.plusDays(89), 90));
        assertEquals(0, PassMath.seasonDay(start, start.plusDays(90), 90), "over");
        assertEquals(90, PassMath.daysLeft(start, start, 90));
        assertEquals(1, PassMath.daysLeft(start, start.plusDays(89), 90));
    }

    @Test
    void catchUpWhileFarBehindTheDayLine() {
        // Day 30: day line 33 levels; 15+ behind = level 18 or lower.
        assertEquals(1.5, PassMath.catchUp(true, 30, 18, 100, 1.1, 15, 1.5));
        assertEquals(1.0, PassMath.catchUp(true, 30, 19, 100, 1.1, 15, 1.5));
        assertEquals(1.0, PassMath.catchUp(false, 30, 0, 100, 1.1, 15, 1.5), "off");
        assertEquals(1.0, PassMath.catchUp(true, 0, 0, 100, 1.1, 15, 1.5), "no season day");
        // Late in the season the line is capped at the last level.
        assertEquals(1.5, PassMath.catchUp(true, 95, 85, 100, 1.1, 15, 1.5));
        assertEquals(1.0, PassMath.catchUp(true, 95, 86, 100, 1.1, 15, 1.5));
    }

    @Test
    void appliedXpIsCappedAndMultiplied() {
        assertEquals(150, PassMath.applied(100, 1.5, 10_000));
        assertEquals(100, PassMath.applied(100, 1.0, 10_000));
        assertEquals(15_000, PassMath.applied(50_000, 1.5, 10_000), "cut to the cap before the bonus");
        assertEquals(0, PassMath.applied(0, 1.5, 10_000));
        assertEquals(0, PassMath.applied(-5, 1.5, 10_000));
        assertEquals(37, PassMath.applied(25, 1.5, 10_000), "rounded down");
    }

    @Test
    void pouchesScaleWithTheTier() {
        long[] ref = {827, 1467, 2560};
        int[] levels = {0, 5, 10};
        assertEquals(1, PassMath.tierFor(0, levels));
        assertEquals(2, PassMath.tierFor(7, levels));
        assertEquals(3, PassMath.tierFor(99, levels));
        assertEquals(1654, PassMath.money(2, 1, ref));
        assertEquals(5120, PassMath.money(2, 9, ref), "tiers past the table use the last value");
        assertEquals(0, PassMath.money(2, 1, new long[0]));
        assertEquals(20, PassMath.crystals(20, 1, 1.3));
        assertEquals(34, PassMath.crystals(20, 3, 1.3));
    }
}

package net.skybornmc.skyblock.achievements;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressMathTest {

    private static AchievementDef def(Long... goals) {
        return new AchievementDef("x", "X", List.of("d"), "stone", "a", Metric.ITEMS_SOLD, List.of(goals), null, false);
    }

    @Test
    void tiersAndNextGoal() {
        AchievementDef d = def(10L, 100L, 1000L);
        assertEquals(0, ProgressMath.tiersDone(d, 0));
        assertEquals(0, ProgressMath.tiersDone(d, 9));
        assertEquals(1, ProgressMath.tiersDone(d, 10));
        assertEquals(2, ProgressMath.tiersDone(d, 999));
        assertEquals(3, ProgressMath.tiersDone(d, 5000));
        assertEquals(10, ProgressMath.nextGoal(d, 0));
        assertEquals(100, ProgressMath.nextGoal(d, 10));
        assertEquals(1000, ProgressMath.nextGoal(d, 5000));
        assertFalse(ProgressMath.complete(d, 999));
        assertTrue(ProgressMath.complete(d, 1000));
    }

    @Test
    void fractionAndBar() {
        AchievementDef d = def(10L, 100L);
        assertEquals(0.5, ProgressMath.fraction(d, 5), 1e-9);
        assertEquals(0.5, ProgressMath.fraction(d, 50), 1e-9);
        assertEquals(1.0, ProgressMath.fraction(d, 100), 1e-9);
        assertEquals(0, ProgressMath.filled(0.0, 10));
        assertEquals(1, ProgressMath.filled(0.01, 10), "started shows one segment");
        assertEquals(9, ProgressMath.filled(0.999, 10), "never full before done");
        assertEquals(10, ProgressMath.filled(1.0, 10));
        assertEquals(99, ProgressMath.percent(0.999));
    }

    @Test
    void newTiersSkipWhatWasUnlocked() {
        AchievementDef d = def(10L, 100L, 1000L);
        assertEquals(List.of(1, 2), ProgressMath.newTiers(d, 150, 0));
        assertEquals(List.of(2), ProgressMath.newTiers(d, 150, 1));
        assertEquals(List.of(), ProgressMath.newTiers(d, 150, 2));
        assertEquals(List.of(), ProgressMath.newTiers(d, 5, 0));
        assertEquals(List.of(), ProgressMath.newTiers(d, 5000, 5), "more unlocked than tiers (goals removed later)");
    }

    @Test
    void addSaturates() {
        assertEquals(Long.MAX_VALUE, ProgressMath.add(Long.MAX_VALUE - 1, 5));
        assertEquals(7, ProgressMath.add(3, 4));
        assertEquals("IV", ProgressMath.roman(4));
        assertEquals("12", ProgressMath.roman(12));
    }

    @Test
    void valueFormats() {
        assertEquals("1,250", ValueFormat.format(Metric.ITEMS_SOLD, 1250));
        assertEquals("$12,500", ValueFormat.format(Metric.MONEY_EARNED, 12_500));
        assertEquals("2h 15m", ValueFormat.format(Metric.PLAYTIME_MINUTES, 135));
        assertEquals("10h", ValueFormat.compact(Metric.PLAYTIME_MINUTES, 600));
        assertEquals("10m", ValueFormat.compact(Metric.PLAYTIME_MINUTES, 10));
        assertEquals("$1M", ValueFormat.compact(Metric.MONEY_EARNED, 1_000_000));
        assertEquals("10k", ValueFormat.compact(Metric.ITEMS_SOLD, 10_000));
    }
}

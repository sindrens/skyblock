package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.LevelSource;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The island level formula (PHASE-5 M5.2): source math, caps and the plan's month-1 example. */
class LevelMathTest {

    private static final LevelMath.Weights W = LevelMath.Weights.of(new LevelConfig());

    @Test
    void planExampleRegularSoloMonthOne() {
        // Pillars 48+44+42+36 = 170, 96 upgrade levels + 10 masteries = 100, 25 achievement points, skill total 190,
        // Depths 8+7+7, lowest bank $20M, block worth $6M → 384 points, level 38 (PHASE-5 M5.2 example).
        LevelMath.Inputs in = new LevelMath.Inputs(170, 100, 25, 0, List.of(190), 22, 20_000_000L, 6_000_000);
        LevelMath.Result r = LevelMath.calculate(in, W);
        assertEquals(170, r.points().get(LevelSource.PILLARS), 1e-9);
        assertEquals(19, r.points().get(LevelSource.SKILLS), 1e-9);
        assertEquals(22, r.points().get(LevelSource.DEPTHS), 1e-9);
        assertEquals(9.2, r.points().get(LevelSource.BANK), 0.01);
        assertEquals(38.7, r.points().get(LevelSource.BLOCKS), 0.05);
        assertEquals(86, r.limits().get(LevelSource.BLOCKS), 0.5, "the plan's 'cap would be 86'");
        assertEquals(384, r.total(), 1.0);
        assertEquals(38, r.level());
        assertEquals(295, r.earned(), 1e-9, "pillars + upgrades + achievements + collections");
    }

    @Test
    void bankIsLogScaleFromBaseAndNeverNegative() {
        assertEquals(0, LevelMath.bankPoints(0, 4, 100_000));
        assertEquals(0, LevelMath.bankPoints(50_000, 4, 100_000));
        assertEquals(0, LevelMath.bankPoints(100_000, 4, 100_000));
        assertEquals(4, LevelMath.bankPoints(1_000_000, 4, 100_000), 1e-9);
        assertEquals(8, LevelMath.bankPoints(10_000_000, 4, 100_000), 1e-9, "10x the money is only +4 points");
        assertEquals(24, LevelMath.bankPoints(100_000_000_000L, 4, 100_000), 1e-9, "a billionaire gets 24 raw points");
    }

    @Test
    void blocksUseTheSquareRoot() {
        assertEquals(0, LevelMath.blockPoints(0, 0.5, 1000));
        assertEquals(5, LevelMath.blockPoints(100_000, 0.5, 1000), 1e-9);
        assertEquals(10, LevelMath.blockPoints(400_000, 0.5, 1000), 1e-9, "4x the worth is only 2x the points");
        assertEquals(0, LevelMath.blockPoints(Double.NaN, 0.5, 1000));
    }

    @Test
    void skillsAverageSoAltsLowerIt() {
        assertEquals(0, LevelMath.skillsPoints(List.of(), 10));
        assertEquals(20, LevelMath.skillsPoints(List.of(200), 10), 1e-9);
        assertEquals(10, LevelMath.skillsPoints(List.of(200, 0), 10), 1e-9, "an alt with no skills halves it");
    }

    @Test
    void capLimitsAShareOfTheTotal() {
        assertEquals(25, LevelMath.capLimit(0.2, 100), 1e-9, "20% of a total of 125 = 25");
        assertEquals(0, LevelMath.capLimit(0.2, 0), 1e-9);
        assertTrue(Double.isInfinite(LevelMath.capLimit(0, 100)));
    }

    @Test
    void cappedSourceNeverPassesItsShare() {
        Map<LevelSource, Double> raw = new EnumMap<>(LevelSource.class);
        raw.put(LevelSource.PILLARS, 80.0);
        raw.put(LevelSource.BLOCKS, 1000.0);
        Map<LevelSource, Double> limits = new EnumMap<>(LevelSource.class);
        Map<LevelSource, Double> out = LevelMath.applyCaps(raw, Map.of(LevelSource.BLOCKS, 20.0), limits);
        assertEquals(20, out.get(LevelSource.BLOCKS), 1e-9);
        assertEquals(0.2, out.get(LevelSource.BLOCKS) / LevelMath.total(out.values()), 1e-9, "exactly 20% of the total");
    }

    @Test
    void allCapsHoldTogether() {
        Map<LevelSource, Double> raw = new EnumMap<>(LevelSource.class);
        raw.put(LevelSource.PILLARS, 100.0);
        raw.put(LevelSource.BANK, 50.0);    // cap 5%
        raw.put(LevelSource.BLOCKS, 50.0);  // cap 20%
        Map<LevelSource, Double> limits = new EnumMap<>(LevelSource.class);
        Map<LevelSource, Double> out = LevelMath.applyCaps(raw, Map.of(LevelSource.BANK, 5.0, LevelSource.BLOCKS, 20.0), limits);
        double total = LevelMath.total(out.values());
        assertEquals(100 / 0.75, total, 1e-9);
        assertEquals(0.05, out.get(LevelSource.BANK) / total, 1e-9);
        assertEquals(0.20, out.get(LevelSource.BLOCKS) / total, 1e-9);
        // Each capped source equals cap / (1 - cap) x the others (the plan's formula) at the end.
        assertEquals(0.05 / 0.95 * (total - out.get(LevelSource.BANK)), out.get(LevelSource.BANK), 1e-9);
        assertEquals(0.20 / 0.80 * (total - out.get(LevelSource.BLOCKS)), out.get(LevelSource.BLOCKS), 1e-9);
        assertEquals(out.get(LevelSource.BLOCKS), limits.get(LevelSource.BLOCKS), 1e-9);
    }

    @Test
    void oneCapOverLeavesTheOtherFree() {
        Map<LevelSource, Double> raw = new EnumMap<>(LevelSource.class);
        raw.put(LevelSource.PILLARS, 100.0);
        raw.put(LevelSource.BANK, 1.0);
        raw.put(LevelSource.BLOCKS, 500.0);
        Map<LevelSource, Double> out = LevelMath.applyCaps(raw, Map.of(LevelSource.BANK, 5.0, LevelSource.BLOCKS, 20.0),
                new EnumMap<>(LevelSource.class));
        assertEquals(1.0, out.get(LevelSource.BANK), 1e-9);
        assertEquals(101 * 0.25, out.get(LevelSource.BLOCKS), 1e-9);
    }

    @Test
    void sourceUnderItsCapIsUntouched() {
        Map<LevelSource, Double> raw = new EnumMap<>(LevelSource.class);
        raw.put(LevelSource.PILLARS, 100.0);
        raw.put(LevelSource.BLOCKS, 10.0);
        Map<LevelSource, Double> out = LevelMath.applyCaps(raw, Map.of(LevelSource.BLOCKS, 20.0), new EnumMap<>(LevelSource.class));
        assertEquals(10, out.get(LevelSource.BLOCKS), 1e-9);
    }

    @Test
    void moneyCantBuyTheLevel() {
        // A billionaire with an empty island: no pillars, no members' skills, huge bank and block worth.
        LevelMath.Inputs rich = new LevelMath.Inputs(0, 0, 0, 0, List.of(), 0, 1_000_000_000_000L, 1e12);
        LevelMath.Result r = LevelMath.calculate(rich, W);
        assertEquals(0, r.total(), 1e-9, "capped live sources can't carry each other");
        assertEquals(0, r.level());
        // The same money next to a modest earned base adds at most its caps.
        LevelMath.Inputs earned = new LevelMath.Inputs(100, 0, 0, 0, List.of(), 0, 1_000_000_000_000L, 1e12);
        LevelMath.Result r2 = LevelMath.calculate(earned, W);
        assertTrue(r2.points().get(LevelSource.BLOCKS) / r2.total() <= 0.2 + 1e-9);
        assertTrue(r2.points().get(LevelSource.BANK) / r2.total() <= 0.05 + 1e-9);
        assertTrue(r2.level() < 14, "level " + r2.level());
    }

    @Test
    void levelAndProgress() {
        assertEquals(0, LevelMath.level(0, 10));
        assertEquals(0, LevelMath.level(9.99, 10));
        assertEquals(38, LevelMath.level(380, 10));
        assertEquals(38, LevelMath.level(379.99999999999, 10), "float noise never loses a level");
        assertEquals(0.4, LevelMath.progress(384, 10), 1e-9);
        assertEquals(0, LevelMath.progress(380, 10), 1e-6);
        assertEquals(0, LevelMath.level(Double.NaN, 10));
    }

    @Test
    void brokenProviderValuesCountZero() {
        LevelMath.Inputs in = new LevelMath.Inputs(-5, Double.NaN, -3, Double.POSITIVE_INFINITY, List.of(-10), -2, -1, -1);
        LevelMath.Result r = LevelMath.calculate(in, W);
        assertEquals(0, r.total(), 1e-9);
    }
}

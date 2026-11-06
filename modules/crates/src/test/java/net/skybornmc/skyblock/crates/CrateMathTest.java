package net.skybornmc.skyblock.crates;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateMathTest {

    @Test
    void boundKeysGoFirst() {
        CrateMath.KeyUse use = CrateMath.keys(3, 5, 10, 64);
        assertEquals(new CrateMath.KeyUse(3, 5), use);
        assertTrue(use.boundAt(0));
        assertTrue(use.boundAt(2));
        assertFalse(use.boundAt(3));
        assertEquals(new CrateMath.KeyUse(1, 0), CrateMath.keys(3, 5, 1, 64));
        assertEquals(new CrateMath.KeyUse(0, 1), CrateMath.keys(0, 5, 1, 64));
        assertEquals(new CrateMath.KeyUse(0, 0), CrateMath.keys(0, 0, 10, 64));
    }

    @Test
    void openAllIsCappedPerClick() {
        assertEquals(64, CrateMath.keys(100, 100, Integer.MAX_VALUE, 64).total());
        assertEquals(new CrateMath.KeyUse(60, 4), CrateMath.keys(60, 100, 64, 64));
        assertEquals(new CrateMath.KeyUse(0, 0), CrateMath.keys(-5, -1, 10, 64), "broken counters never open anything");
    }

    @Test
    void tierFromSkillLevel() {
        int[] levels = {0, 5, 10, 15};
        assertEquals(1, CrateMath.tierFor(0, levels));
        assertEquals(1, CrateMath.tierFor(4, levels));
        assertEquals(2, CrateMath.tierFor(5, levels));
        assertEquals(4, CrateMath.tierFor(150, levels));
    }

    @Test
    void moneyPouchIsMinutesOfReference() {
        long[] ref = {827, 1_467, 2_560};
        assertEquals(3 * 827, CrateMath.money(3, 1, ref));
        assertEquals(15 * 2_560, CrateMath.money(15, 3, ref));
        assertEquals(15 * 2_560, CrateMath.money(15, 12, ref), "above the list: the last value");
        assertEquals(0, CrateMath.money(0, 1, ref));
        assertEquals(0, CrateMath.money(5, 1, new long[0]));
        assertEquals(Long.MAX_VALUE, CrateMath.money(Integer.MAX_VALUE, 1, new long[]{Long.MAX_VALUE / 2}));
    }

    @Test
    void bundledReferenceMatchesThePlanExamples() {
        CratesSettings s = new CratesSettings();
        long[] ref = CrateMath.longs(s.refPerMinute, "x", new ArrayList<>());
        assertEquals(12, ref.length);
        assertEquals(83_300, CrateMath.money(1, 1, ref), 1_000, "plan: 1 min of REF is about $833 at tier 1 (x100 since batch 10)");
        assertEquals(4_270_000, CrateMath.money(1, 8, ref), 10_000, "plan: about $42.7k at tier 8 (x100)");
    }

    @Test
    void crystalPouchGrowsPerTier() {
        assertEquals(15, CrateMath.crystals(15, 1, 1.3));
        assertEquals(20, CrateMath.crystals(15, 2, 1.3)); // 19.5 rounds up
        assertEquals(Math.round(100 * Math.pow(1.3, 7)), CrateMath.crystals(100, 8, 1.3));
        assertEquals(0, CrateMath.crystals(0, 5, 1.3));
        assertEquals(15, CrateMath.crystals(15, 5, Double.NaN), "a broken growth means no growth");
    }

    @Test
    void numberListsReportBadValues() {
        List<String> problems = new ArrayList<>();
        assertArrayEquals(new long[]{1, 2_000}, CrateMath.longs(List.of("1", "x", "-3", "2_000"), "where", problems));
        assertEquals(2, problems.size());
        problems.clear();
        assertArrayEquals(new int[]{0, 5, 9}, CrateMath.levels(List.of("0", "5", "3", "9", "y"), "where", problems));
        assertEquals(2, problems.size());
    }

    @Test
    void percentText() {
        assertEquals("12", CrateMath.percent(12.0));
        assertEquals("1.5", CrateMath.percent(1.5));
        assertEquals("0.25", CrateMath.percent(0.25));
        assertEquals("33.33", CrateMath.percent(100.0 / 3));
    }

    /** Batch 9: "Top reward in N opens" matches RollEngine (forced when counter + 1 >= pity). */
    @Test
    void pityBarAndOpensLeft() {
        assertEquals(30, CrateMath.opensLeft(0, 30));
        assertEquals(1, CrateMath.opensLeft(29, 30), "the next open is sure");
        assertEquals(1, CrateMath.opensLeft(40, 30), "never 0 or less");
        assertEquals(0, CrateMath.opensLeft(5, 0), "no pity");
        for (int counter = 0; counter < 30; counter++) {
            int left = CrateMath.opensLeft(counter, 30);
            assertEquals(counter + left >= 30, true);
            assertEquals(counter + left - 1 + 1 >= 30, counter + left >= 30);
        }
        assertEquals(0, CrateMath.pityBar(0, 30).chars().filter(c -> c == 0x25cf).count() - 10);
        assertTrue(CrateMath.pityBar(15, 30).startsWith("<c:flame>\u25cf\u25cf\u25cf\u25cf\u25cf</c:flame>"));
        assertTrue(CrateMath.pityBar(99, 30).endsWith("<c:locked></c:locked>"), "full, never more than 10");
    }
}

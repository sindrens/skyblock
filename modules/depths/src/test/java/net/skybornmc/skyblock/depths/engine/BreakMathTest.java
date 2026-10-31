package net.skybornmc.skyblock.depths.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreakMathTest {

    @Test
    void vanillaBreakTimes() {
        // Iron ore (hardness 3) with an iron pickaxe (speed 6): 6/3/30 = 1/15 per tick = 15 ticks (0.75 s).
        assertEquals(15, BreakMath.expectedTicks(BreakMath.progressPerTick(3f, 6f, true)));
        // Stone (1.5) with a wooden pickaxe (2): 2/1.5/30 → 22.5 → 23 ticks.
        assertEquals(23, BreakMath.expectedTicks(BreakMath.progressPerTick(1.5f, 2f, true)));
        // Iron ore by hand (speed 1, can't harvest): 1/3/100 → 300 ticks (15 s).
        assertEquals(300, BreakMath.expectedTicks(BreakMath.progressPerTick(3f, 1f, false)));
        // Oak log (2) by hand (1, harvestable): 60 ticks (3 s).
        assertEquals(60, BreakMath.expectedTicks(BreakMath.progressPerTick(2f, 1f, true)));
    }

    @Test
    void instantAndUnbreakable() {
        assertEquals(0, BreakMath.expectedTicks(BreakMath.progressPerTick(0f, 1f, true)));
        assertEquals(0, BreakMath.expectedTicks(1.5f));
        assertEquals(BreakMath.UNBREAKABLE, BreakMath.expectedTicks(BreakMath.progressPerTick(-1f, 100f, true)));
        assertEquals(BreakMath.UNBREAKABLE, BreakMath.expectedTicks(0f));
        assertEquals(BreakMath.UNBREAKABLE, BreakMath.expectedTicks(Float.NaN));
        assertEquals(Long.MAX_VALUE, BreakMath.expectedMillis(BreakMath.UNBREAKABLE));
    }

    @Test
    void exactFractionsDoNotRoundUpAnExtraTick() {
        assertEquals(30, BreakMath.expectedTicks(1f / 30f));
        assertEquals(20, BreakMath.expectedTicks(0.05f));
        assertEquals(3, BreakMath.expectedTicks(1f / 3f));
    }

    @Test
    void minimumAllowedTime() {
        // 15 ticks = 750 ms; 70% = 525 ms; minus 100 ms grace = 425 ms.
        assertEquals(425, BreakMath.minAllowedMillis(15, 0.7, 100));
        assertEquals(0, BreakMath.minAllowedMillis(2, 0.7, 100));
        assertEquals(Long.MAX_VALUE, BreakMath.minAllowedMillis(BreakMath.UNBREAKABLE, 0.7, 100));
    }

    @Test
    void flagsOnlyImpossibleBreaks() {
        assertTrue(BreakMath.tooFast(100, 15, 0.7, 100));     // nuker
        assertTrue(BreakMath.tooFast(0, 15, 0.7, 100));       // no start packet at all
        assertFalse(BreakMath.tooFast(425, 15, 0.7, 100));    // exactly at the limit
        assertFalse(BreakMath.tooFast(750, 15, 0.7, 100));    // normal
        assertFalse(BreakMath.tooFast(5000, 15, 0.7, 100));   // slow
        assertFalse(BreakMath.tooFast(0, 0, 0.7, 100));       // instant break
        assertFalse(BreakMath.tooFast(0, 1, 0.7, 100));       // one tick
        assertTrue(BreakMath.tooFast(1_000_000, BreakMath.UNBREAKABLE, 0.7, 100));
    }

    @Test
    void shownBlockHardnessScalesTheCheck() {
        // Real coal ore (hardness 3) with a wooden pickaxe (2, harvests): 2/3/30 per tick = 45 ticks.
        float coal = BreakMath.progressPerTick(3f, 2f, true);
        assertEquals(45, BreakMath.expectedTicks(coal));
        // Shown iron ore: same hardness, but wood can't harvest it: /100 instead of /30 -> 150 ticks, like the client.
        float iron = BreakMath.scaledSpeed(coal, 3f, 2f, true, 3f, 2f, false);
        assertEquals(150, BreakMath.expectedTicks(iron));
        // Shown deepslate iron (4.5) with a stone pickaxe (4, harvests): 4/4.5/30 -> 34 ticks.
        float stoneOnCoal = BreakMath.progressPerTick(3f, 4f, true);
        assertEquals(34, BreakMath.expectedTicks(BreakMath.scaledSpeed(stoneOnCoal, 3f, 4f, true, 4.5f, 4f, true)));
        // Same block shown as real: unchanged (Haste etc. stay in the factor).
        float hasted = coal * 1.4f;
        assertEquals(hasted, BreakMath.scaledSpeed(hasted, 3f, 2f, true, 3f, 2f, true), 1e-6);
    }

    @Test
    void scaledSpeedEdgeCases() {
        assertEquals(1f, BreakMath.scaledSpeed(0.05f, 3f, 2f, true, 0f, 1f, true)); // shown instant
        assertEquals(0f, BreakMath.scaledSpeed(0.05f, 3f, 2f, true, -1f, 1f, true)); // shown unbreakable
        // Real block instant: effects can't be factored out, plain formula for the shown block.
        assertEquals(BreakMath.progressPerTick(3f, 6f, true), BreakMath.scaledSpeed(1f, 0f, 1f, true, 3f, 6f, true), 1e-6);
        assertEquals(BreakMath.progressPerTick(3f, 6f, true), BreakMath.scaledSpeed(0f, 3f, 6f, true, 3f, 6f, true), 1e-6);
    }
}

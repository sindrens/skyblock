package net.skybornmc.skyblock.depths.satchel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatchelMathTest {

    private final List<SatchelMath.Upgrade> ups = SatchelMath.upgrades(List.of("256=25000", "512=100000", "1024=400000"), p -> {
    });

    @Test
    void mockupNumbers() {
        // PHASE-4 mockup: "Satchel 412 / 640" with Mining 12 + Foraging 12 and no upgrades.
        assertEquals(640, SatchelMath.capacity(256, 16, 12, 16, 12, ups, 0, 0, 100_000));
    }

    @Test
    void upgradesAddInOrderAndStopAtTheList() {
        assertEquals(256 + 256, SatchelMath.capacity(256, 16, 0, 16, 0, ups, 1, 0, 100_000));
        assertEquals(256 + 256 + 512 + 1024, SatchelMath.capacity(256, 16, 0, 16, 0, ups, 3, 0, 100_000));
        assertEquals(256 + 256 + 512 + 1024, SatchelMath.capacity(256, 16, 0, 16, 0, ups, 99, 0, 100_000));
        assertEquals(256, SatchelMath.capacity(256, 16, 0, 16, 0, ups, -4, 0, 100_000));
    }

    @Test
    void bonusAndCaps() {
        assertEquals(256 + 100, SatchelMath.capacity(256, 16, 0, 16, 0, ups, 0, 100, 100_000));
        assertEquals(256, SatchelMath.capacity(256, 16, 0, 16, 0, ups, 0, -50, 100_000)); // negative bonus ignored
        assertEquals(1000, SatchelMath.capacity(256, 16, 150, 16, 150, ups, 3, 0, 1000));
        assertEquals(1, SatchelMath.capacity(0, 0, 0, 0, 0, List.of(), 0, 0, 10));
        // Max skills 150 + 150 do not overflow.
        assertEquals(256L + 16 * 300, SatchelMath.capacity(256, 16, 150, 16, 150, List.of(), 0, 0, Integer.MAX_VALUE));
    }

    @Test
    void parsesUpgradesAndReportsBrokenOnes() {
        List<String> problems = new ArrayList<>();
        List<SatchelMath.Upgrade> parsed = SatchelMath.upgrades(List.of("256=25_000", "x=1", "0=5", "12", "64=1,500"), problems::add);
        assertEquals(List.of(new SatchelMath.Upgrade(256, 25_000), new SatchelMath.Upgrade(64, 1_500)), parsed);
        assertEquals(3, problems.size());
    }

    @Test
    void fullAndFree() {
        assertTrue(SatchelMath.full(640, 640));
        assertTrue(SatchelMath.full(700, 640));
        assertFalse(SatchelMath.full(639, 640));
        assertEquals(0, SatchelMath.free(700, 640));
        assertEquals(64, SatchelMath.percent(412, 640));
        assertEquals(100, SatchelMath.percent(5, 0));
    }

    @Test
    void takeOutAmounts() {
        assertEquals(2 * 64 + 10, SatchelMath.fit(2, 64, 10));
        assertEquals(64, SatchelMath.takeAmount(64, 500, 1000));
        assertEquals(30, SatchelMath.takeAmount(64, 30, 1000));
        assertEquals(20, SatchelMath.takeAmount(500, 500, 20));
        assertEquals(0, SatchelMath.takeAmount(64, 500, 0));
    }

    @Test
    void stacks() {
        assertArrayEquals(new int[]{64, 64, 2}, SatchelMath.stacks(130, 64));
        assertArrayEquals(new int[]{16}, SatchelMath.stacks(16, 64));
        assertArrayEquals(new int[0], SatchelMath.stacks(0, 64));
        assertEquals(1563, SatchelMath.stacks(100_000, 64).length);
    }
}

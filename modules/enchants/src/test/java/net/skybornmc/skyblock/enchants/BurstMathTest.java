package net.skybornmc.skyblock.enchants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ember Momentum, Phoenix Haste and the speed limits (family cap, 6 blocks/s hard cap). */
class BurstMathTest {

    @Test
    void momentumStacksEvery40ActionsAndResetsWhenIdle() {
        BurstMath.Momentum m = BurstMath.Momentum.NONE;
        long t = 0;
        for (int i = 0; i < 40; i++) {
            m = BurstMath.momentum(m, t += 200, 40, 4000, 3);
        }
        assertEquals(1, m.stacks());
        for (int i = 0; i < 200; i++) {
            m = BurstMath.momentum(m, t += 200, 40, 4000, 3);
        }
        assertEquals(3, m.stacks(), "max 3 stacks");
        assertEquals(3, BurstMath.stacks(m, t + 4000, 4000));
        assertEquals(0, BurstMath.stacks(m, t + 4001, 4000), "all stacks lost after 4 s idle");
        m = BurstMath.momentum(m, t + 5000, 40, 4000, 3);
        assertEquals(0, m.stacks());
        assertEquals(1, m.actions());
    }

    @Test
    void hasteStartsByChanceAndCoolsDownAfterTheBurst() {
        BurstMath.Haste h = BurstMath.haste(BurstMath.Haste.NONE, 1000, 0.5, 2, 10_000, 20_000);
        assertFalse(BurstMath.active(h, 1000), "roll 50 >= 2%: no burst");
        h = BurstMath.haste(h, 1000, 0.01, 2, 10_000, 20_000);
        assertTrue(BurstMath.active(h, 1000));
        assertTrue(BurstMath.active(h, 10_999));
        assertFalse(BurstMath.active(h, 11_000));
        BurstMath.Haste again = BurstMath.haste(h, 20_000, 0.0, 2, 10_000, 20_000);
        assertEquals(h, again, "still cooling down");
        assertTrue(BurstMath.active(BurstMath.haste(h, 31_000, 0.0, 2, 10_000, 20_000), 31_000));
    }

    @Test
    void burstsRespectTheFamilyCapAndTheBreakRate() {
        assertEquals(40, BurstMath.allowed(40, 30, 200, 3, 6), 1e-9);
        assertEquals(20, BurstMath.allowed(40, 180, 200, 3, 6), 1e-9, "only the room below the cap");
        assertEquals(0, BurstMath.allowed(40, 250, 200, 3, 6), 1e-9);
        assertEquals(0, BurstMath.allowed(40, 0, 200, 6, 6), 1e-9, "at 6 blocks/s no burst applies");
        assertEquals(0, BurstMath.allowed(0, 0, 200, 0, 6), 1e-9);
    }

    @Test
    void rateCountsTheLastSecond() {
        BurstMath.Rate r = new BurstMath.Rate(16);
        for (int i = 0; i < 8; i++) {
            r.add(i * 100L);
        }
        assertEquals(8, r.lastSecond(800));
        assertEquals(2, r.lastSecond(1550));
        assertEquals(0, r.lastSecond(5000));
    }
}

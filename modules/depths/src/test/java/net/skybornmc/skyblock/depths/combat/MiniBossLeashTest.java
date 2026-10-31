package net.skybornmc.skyblock.depths.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniBossLeashTest {

    private static MiniBossLeash leash() {
        return new MiniBossLeash(0, 100, 0, 11, 6);
    }

    @Test
    void insideTheRingFightsAsUsual() {
        MiniBossLeash l = leash();
        assertEquals(MiniBossLeash.Action.INSIDE, l.check(5, 100, 5, 0, 10_000));
        assertEquals(MiniBossLeash.Action.INSIDE, l.check(11, 104, 0, 0, 10_000), "on the edge, small height difference");
        assertFalse(l.returning());
    }

    @Test
    void pulledOutItWalksBackUntilTheInnerHalf() {
        MiniBossLeash l = leash();
        assertEquals(MiniBossLeash.Action.RETURN, l.check(12, 100, 0, 0, 10_000));
        assertTrue(l.returning());
        assertEquals(MiniBossLeash.Action.RETURN, l.check(9, 100, 0, 500, 10_000), "back in the ring but not deep enough");
        assertEquals(MiniBossLeash.Action.INSIDE, l.check(5, 100, 0, 1000, 10_000), "inner half reached");
        assertFalse(l.returning());
    }

    @Test
    void tooHighOrLowCountsAsOutside() {
        MiniBossLeash l = leash();
        assertEquals(MiniBossLeash.Action.RETURN, l.check(0, 107, 0, 0, 10_000));
        assertEquals(MiniBossLeash.Action.RETURN, l.check(0, 93, 0, 0, 10_000));
    }

    @Test
    void stuckOutsideIsPutBackHome() {
        MiniBossLeash l = leash();
        assertEquals(MiniBossLeash.Action.RETURN, l.check(20, 100, 0, 1_000, 10_000));
        assertEquals(MiniBossLeash.Action.RETURN, l.check(20, 100, 0, 10_999, 10_000));
        assertEquals(MiniBossLeash.Action.TELEPORT, l.check(20, 100, 0, 11_000, 10_000));
        assertFalse(l.returning(), "the clock starts again after the teleport");
        assertEquals(MiniBossLeash.Action.INSIDE, l.check(0, 100, 0, 11_500, 10_000));
    }

    @Test
    void zeroTeleportTimeNeverTeleports() {
        MiniBossLeash l = leash();
        l.check(30, 100, 0, 0, 0);
        assertEquals(MiniBossLeash.Action.RETURN, l.check(30, 100, 0, 1_000_000, 0));
    }

    @Test
    void healsFivePercentPerSecondNeverAboveMax() {
        // 5%/s of 600 = 30 per second, a check every 10 ticks = 15
        assertEquals(15, MiniBossLeash.heal(300, 600, 5, 10), 1e-9);
        assertEquals(30, MiniBossLeash.heal(300, 600, 5, 20), 1e-9);
        assertEquals(5, MiniBossLeash.heal(595, 600, 5, 10), 1e-9, "capped at max");
        assertEquals(0, MiniBossLeash.heal(600, 600, 5, 10));
        assertEquals(0, MiniBossLeash.heal(300, 600, 0, 10));
        assertEquals(0, MiniBossLeash.heal(0, 600, 5, 10), "a dead mob heals nothing");
        assertEquals(0, MiniBossLeash.heal(300, 0, 5, 10));
    }
}

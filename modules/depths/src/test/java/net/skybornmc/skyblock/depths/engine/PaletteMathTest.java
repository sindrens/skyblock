package net.skybornmc.skyblock.depths.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaletteMathTest {

    /** Mine zone 1 as zones.yml says: coal 0, copper 5, iron 10. */
    private static final int[] M1 = {0, 5, 10};

    @Test
    void activeTierFollowsMiningLevel() {
        assertEquals(1, PaletteMath.activeTier(0, M1, PaletteMath.AUTO));
        assertEquals(1, PaletteMath.activeTier(4, M1, PaletteMath.AUTO));
        assertEquals(2, PaletteMath.activeTier(5, M1, PaletteMath.AUTO));
        assertEquals(2, PaletteMath.activeTier(9, M1, PaletteMath.AUTO));
        assertEquals(3, PaletteMath.activeTier(10, M1, PaletteMath.AUTO));
        assertEquals(3, PaletteMath.activeTier(150, M1, PaletteMath.AUTO));
    }

    @Test
    void tierOneIsAlwaysOpenAndUnknownLevelsStayThere() {
        assertEquals(1, PaletteMath.activeTier(null, M1, PaletteMath.AUTO)); // no skill system: real block only
        assertEquals(1, PaletteMath.activeTier(-3, M1, PaletteMath.AUTO));
        assertEquals(1, PaletteMath.highestOpen(0, new int[]{7, 9})); // tier 1 shown even if its requirement is above
        assertEquals(1, PaletteMath.highestOpen(50, new int[0]));
        assertEquals(1, PaletteMath.highestOpen(50, null));
    }

    @Test
    void unorderedRequirementsStillPickTheHighestOpenIndex() {
        assertEquals(3, PaletteMath.highestOpen(6, new int[]{0, 8, 5}));
        assertEquals(2, PaletteMath.highestOpen(8, new int[]{0, 8, 50}));
    }

    @Test
    void lowerTierChoiceIsClampedToWhatIsOpen() {
        assertEquals(1, PaletteMath.activeTier(12, M1, 1)); // D2: an iron player picks coal
        assertEquals(2, PaletteMath.activeTier(12, M1, 2));
        assertEquals(2, PaletteMath.activeTier(7, M1, 3)); // never a locked tier's rewards
        assertEquals(3, PaletteMath.activeTier(12, M1, 0)); // auto
        assertEquals(3, PaletteMath.activeTier(12, M1, -1));
    }

    @Test
    void packetsOnlyAboveTierOne() {
        assertFalse(PaletteMath.needsPackets(1));
        assertTrue(PaletteMath.needsPackets(2));
        assertTrue(PaletteMath.needsPackets(3));
    }

    @Test
    void unlockMomentOnlyOnLevelUpsThatRaiseTheTier() {
        assertTrue(PaletteMath.isUnlock(1, 2, true));
        assertTrue(PaletteMath.isUnlock(1, 3, true));
        assertFalse(PaletteMath.isUnlock(2, 2, true));
        assertFalse(PaletteMath.isUnlock(3, 2, true));
        assertFalse(PaletteMath.isUnlock(1, 3, false)); // join, profile switch, periodic check: quiet
    }

    @Test
    void rewardTierPrefersTheShownTier() {
        assertEquals(3, PaletteMath.rewardTier(3, 1)); // iron shown on the coal field: tier 3 table
        assertEquals(4, PaletteMath.rewardTier(0, 4)); // no palette: the zone's own tier
    }

    @Test
    void ringsSplitTheSortedDistances() {
        int[] d2 = {0, 1, 3, 4, 8, 9, 16, 25, 100};
        int a = PaletteMath.ringEnd(d2, 0, 2.0); // < 4
        assertEquals(3, a);
        int b = PaletteMath.ringEnd(d2, a, 4.0); // < 16
        assertEquals(6, b);
        assertEquals(8, PaletteMath.ringEnd(d2, b, 6.0));
        assertEquals(9, PaletteMath.ringEnd(d2, 8, 1000.0));
        assertEquals(9, PaletteMath.ringEnd(d2, 9, 1000.0));
        assertEquals(0, PaletteMath.ringEnd(new int[0], 0, 5));
    }

    @Test
    void perTickLimits() {
        assertEquals(300, PaletteMath.perTick(true, 300, 0));
        assertEquals(Integer.MAX_VALUE, PaletteMath.perTick(false, 300, 0));
        assertEquals(300, PaletteMath.perTick(false, 0, 300));
        assertEquals(Integer.MAX_VALUE, PaletteMath.perTick(true, 0, 300));
    }

    @Test
    void sortNearestOrdersKeysAndReturnsDistances() {
        long far = BlockKeys.block(10, 100, 0);
        long near = BlockKeys.block(1, 100, 0);
        long mid = BlockKeys.block(0, 103, 4);
        long[] keys = {far, near, mid};
        int[] d2 = PaletteMath.sortNearest(keys, 0, 100, 0);
        assertArrayEquals(new long[]{near, mid, far}, keys);
        assertArrayEquals(new int[]{1, 25, 100}, d2);
        long neg = BlockKeys.block(-140, 98, -28);
        long[] one = {neg};
        assertArrayEquals(new int[]{0}, PaletteMath.sortNearest(one, -140, 98, -28));
        assertEquals(0, PaletteMath.sortNearest(new long[0], 0, 0, 0).length);
    }

    @Test
    void sortNearestRejectsAbsurdSizes() {
        assertThrows(IllegalArgumentException.class, () -> PaletteMath.sortNearest(new long[1 << 24], 0, 0, 0));
    }
}

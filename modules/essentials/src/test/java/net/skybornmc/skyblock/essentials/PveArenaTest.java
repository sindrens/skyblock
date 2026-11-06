package net.skybornmc.skyblock.essentials;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 6 Nov 2026 (FULL_PLAN.md 6): ten rings, tier 1 outside, tier 10 in the middle, kills open the next one. */
class PveArenaTest {

    @Test
    void ringsFromOutsideIn() {
        assertEquals(10, PveArena.ringAt(0, 0), "the centre is tier 10");
        assertEquals(1, PveArena.ringAt(PveArena.HALF, 0), "the outer edge is tier 1");
        assertEquals(1, PveArena.ringAt(-PveArena.HALF + 1, PveArena.HALF - 2));
        assertEquals(0, PveArena.ringAt(PveArena.HALF + 1, 0), "outside the arena");
        assertEquals(9, PveArena.ringAt(PveArena.RING, 3));
    }

    @Test
    void killsOpenTheNextTierInOrder() {
        long[] k = new long[PveArena.TIERS];
        assertEquals(1, PveArena.unlocked(k, PveArena.TIER_LIST));
        k[0] = 200;
        assertEquals(2, PveArena.unlocked(k, PveArena.TIER_LIST), "200 zombies open the husks");
        k[2] = 1_000_000;
        assertEquals(2, PveArena.unlocked(k, PveArena.TIER_LIST), "no skipping a tier");
        for (int i = 0; i < k.length; i++) {
            k[i] = 1_000_000;
        }
        assertEquals(10, PveArena.unlocked(k, PveArena.TIER_LIST));
        for (int i = 1; i < PveArena.TIER_LIST.size(); i++) {
            assertTrue(PveArena.TIER_LIST.get(i).pay() > PveArena.TIER_LIST.get(i - 1).pay(), "inner rings pay more");
            assertTrue(PveArena.TIER_LIST.get(i).health() > PveArena.TIER_LIST.get(i - 1).health());
        }
        assertTrue(PveArena.X - PveArena.HALF > -3000, "inside the world border");
    }
}

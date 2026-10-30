package net.skybornmc.skyblock.perks;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Owner answer 377: /ec and /craft are blocked in the warzone, the boss arena and 10 s after combat. */
class PerkBlocksTest {

    @Test
    void freeOutsideCombatAreas() {
        assertEquals(Optional.empty(), PerkBlocks.check(false, false, 0));
    }

    @Test
    void warzoneBossArenaAndCombatTagBlock() {
        assertEquals(Optional.of(PerkBlocks.Reason.WARZONE), PerkBlocks.check(true, false, 0));
        assertEquals(Optional.of(PerkBlocks.Reason.BOSS_ARENA), PerkBlocks.check(false, true, 0));
        assertEquals(Optional.of(PerkBlocks.Reason.COMBAT), PerkBlocks.check(false, false, 4_000));
    }

    @Test
    void placeReasonWinsOverTheTimer() {
        assertEquals(Optional.of(PerkBlocks.Reason.WARZONE), PerkBlocks.check(true, true, 9_000));
        assertEquals("boss-arena", PerkBlocks.Reason.BOSS_ARENA.key());
    }
}

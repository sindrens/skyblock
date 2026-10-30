package net.skybornmc.skyblock.common.profile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Slot unlock rules (P6, PR5) and the season reset rule (P8). */
class SlotRulesTest {

    private static SlotRules.Unlock gems(int slot) {
        return new SlotRules.Unlock(slot, SlotSource.GEMS, 0, 1L);
    }

    @Test
    void slotOneIsAlwaysFree() {
        assertEquals(Optional.of(SlotSource.FREE), SlotRules.source(1, List.of(), s -> false));
        assertEquals(Optional.of(SlotRules.BuyProblem.FREE_SLOT), SlotRules.canBuy(1, List.of(), s -> false, 500));
    }

    @Test
    void slotsTwoAndThreeNeedGemsOrRank() {
        assertFalse(SlotRules.unlocked(2, List.of(), s -> false));
        assertFalse(SlotRules.unlocked(3, List.of(), s -> false));
        // Knight+: slot 2 by rank only
        Set<Integer> knight = Set.of(2);
        assertEquals(Optional.of(SlotSource.RANK), SlotRules.source(2, List.of(), knight::contains));
        assertFalse(SlotRules.unlocked(3, List.of(), knight::contains));
        // Warlord+: both
        Set<Integer> warlord = Set.of(2, 3);
        assertEquals(3, SlotRules.usable(List.of(), warlord::contains));
        // bought slot 3 without slot 2: slots are independent
        assertTrue(SlotRules.unlocked(3, List.of(gems(3)), s -> false));
        assertFalse(SlotRules.unlocked(2, List.of(gems(3)), s -> false));
        assertEquals(2, SlotRules.usable(List.of(gems(3)), s -> false));
    }

    @Test
    void boughtSlotWinsOverRankAndStaysWhenTheRankIsLost() {
        assertEquals(Optional.of(SlotSource.GEMS), SlotRules.source(2, List.of(gems(2)), s -> true));
        assertTrue(SlotRules.unlocked(2, List.of(gems(2)), s -> false));
    }

    @Test
    void nobodyPaysForASlotTheyHave() {
        assertEquals(Optional.of(SlotRules.BuyProblem.ALREADY_UNLOCKED), SlotRules.canBuy(2, List.of(gems(2)), s -> false, 500));
        assertEquals(Optional.of(SlotRules.BuyProblem.ALREADY_UNLOCKED), SlotRules.canBuy(2, List.of(), s -> s == 2, 500));
        assertEquals(Optional.of(SlotRules.BuyProblem.NOT_FOR_SALE), SlotRules.canBuy(2, List.of(), s -> false, 0));
        assertEquals(Optional.of(SlotRules.BuyProblem.INVALID_SLOT), SlotRules.canBuy(4, List.of(), s -> false, 500));
        assertEquals(Optional.empty(), SlotRules.canBuy(3, List.of(), s -> false, 1_500));
    }

    @Test
    void seasonResetKeepsOnlyGemSlots() {
        List<SlotRules.Unlock> before = List.of(gems(2), new SlotRules.Unlock(3, SlotSource.GRANT, 0, 1L),
                new SlotRules.Unlock(3, SlotSource.RANK, 0, 1L));
        List<SlotRules.Unlock> after = SlotRules.afterSeasonReset(before);
        assertEquals(List.of(gems(2)), after);
        assertTrue(SlotRules.unlocked(2, after, s -> false));
        assertFalse(SlotRules.unlocked(3, after, s -> false));
    }

    @Test
    void rankPermissionNames() {
        assertEquals("skyblock.profiles.slots.2", SlotRules.rankPermission(2));
        assertEquals("skyblock.profiles.slots.3", SlotRules.rankPermission(3));
    }
}

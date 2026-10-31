package net.skybornmc.skyblock.essentials;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** QOL3 auto-refill: where the next stack comes from. */
class AutoRefillTest {

    @Test
    void takesTheFirstMatchingBackpackSlot() {
        Set<Integer> matching = Set.of(14, 20, 30);
        assertEquals(14, AutoRefill.pick(3, matching::contains));
    }

    @Test
    void neverTakesFromHotbarArmorOrOffHand() {
        Set<Integer> matching = Set.of(0, 1, 5, 8, 36, 37, 38, 39, 40);
        assertEquals(-1, AutoRefill.pick(2, matching::contains));
    }

    @Test
    void nothingMatchingMeansNoRefill() {
        assertEquals(-1, AutoRefill.pick(0, slot -> false));
    }

    @Test
    void offHandTargetStillUsesTheBackpack() {
        assertEquals(35, AutoRefill.pick(AutoRefill.OFF_HAND, slot -> slot == 35));
    }
}

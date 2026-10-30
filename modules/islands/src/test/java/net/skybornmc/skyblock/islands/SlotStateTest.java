package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The slot life cycle: free → used → clearing → free, nothing else. */
class SlotStateTest {

    @Test
    void onlyTheCycleIsAllowed() {
        for (SlotState from : SlotState.values()) {
            for (SlotState to : SlotState.values()) {
                boolean expected = (from == SlotState.FREE && to == SlotState.USED)
                        || (from == SlotState.USED && to == SlotState.CLEARING)
                        || (from == SlotState.CLEARING && to == SlotState.FREE);
                assertEquals(expected, from.canMoveTo(to), from + " -> " + to);
            }
        }
    }

    @Test
    void aClearingSlotIsNeverReusedAndStillHoldsBlocks() {
        assertTrue(SlotState.FREE.reusable());
        assertFalse(SlotState.USED.reusable());
        assertFalse(SlotState.CLEARING.reusable());
        assertTrue(SlotState.CLEARING.holdsBlocks());
        assertTrue(SlotState.USED.holdsBlocks());
        assertFalse(SlotState.FREE.holdsBlocks());
        assertFalse(SlotState.CLEARING.canMoveTo(SlotState.USED), "half-cleared slots never get a new island");
    }

    @Test
    void keysMatchTheDatabase() {
        assertEquals("free", SlotState.FREE.key());
        assertEquals("used", SlotState.USED.key());
        assertEquals("clearing", SlotState.CLEARING.key());
        assertEquals(IslandStore.STATE_CLEARING, SlotState.CLEARING.key());
        assertEquals(Optional.of(SlotState.CLEARING), SlotState.byKey(" Clearing "));
        assertEquals(Optional.empty(), SlotState.byKey("gone"));
        assertEquals(Optional.empty(), SlotState.byKey(null));
    }

    @Test
    void slotsMenuFilterCyclesAndCounts() {
        assertEquals(SlotState.USED, SlotsMenu.nextFilter(null));
        assertEquals(SlotState.CLEARING, SlotsMenu.nextFilter(SlotState.USED));
        assertEquals(SlotState.FREE, SlotsMenu.nextFilter(SlotState.CLEARING));
        assertEquals(null, SlotsMenu.nextFilter(SlotState.FREE));
        var rows = java.util.List.of(
                new IslandStore.SlotRow(0, 0, 0, "used", null, 1),
                new IslandStore.SlotRow(1, 1, 0, "clearing", null, 1),
                new IslandStore.SlotRow(2, 1, 1, "free", null, 1),
                new IslandStore.SlotRow(3, 0, 1, "used", null, 1));
        assertEquals(2, SlotsMenu.filter(rows, SlotState.USED).size());
        assertEquals(4, SlotsMenu.filter(rows, null).size());
        assertEquals(java.util.List.of(rows.get(2)), SlotsMenu.filter(rows, SlotState.FREE));
        var counts = SlotsMenu.counts(rows);
        assertEquals(2, counts.get(SlotState.USED));
        assertEquals(1, counts.get(SlotState.CLEARING));
        assertEquals(1, counts.get(SlotState.FREE));
    }

    @Test
    void adminSizesAreOdd() {
        assertEquals(java.util.List.of("1", "3", "5", "7", "9", "11", "13", "15"), IslandAdmin.sizes(15));
        assertEquals(java.util.List.of("1", "3"), IslandAdmin.sizes(4));
    }
}

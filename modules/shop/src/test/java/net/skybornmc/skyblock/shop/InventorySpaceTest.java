package net.skybornmc.skyblock.shop;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Free space is counted before paying: empty slots hold a full stack, matching stacks fill up, others hold nothing. */
class InventorySpaceTest {

    private static List<InventorySpace.Slot> empty() {
        return new ArrayList<>(Collections.nCopies(InventorySpace.STORAGE_SLOTS, InventorySpace.Slot.emptySlot()));
    }

    @Test
    void emptyInventoryFits36Stacks() {
        assertEquals(36 * 64, InventorySpace.fits(empty(), 64));
        assertEquals(36 * 16, InventorySpace.fits(empty(), 16));
        assertEquals(36, InventorySpace.fits(empty(), 1));
    }

    @Test
    void fullInventoryFitsNothing() {
        List<InventorySpace.Slot> slots = new ArrayList<>(Collections.nCopies(36, InventorySpace.Slot.other()));
        assertEquals(0, InventorySpace.fits(slots, 64));
    }

    @Test
    void partialStacksOfTheSameItemCount() {
        List<InventorySpace.Slot> slots = new ArrayList<>(Collections.nCopies(36, InventorySpace.Slot.other()));
        slots.set(0, InventorySpace.Slot.same(60));
        slots.set(5, InventorySpace.Slot.same(1));
        slots.set(9, InventorySpace.Slot.emptySlot());
        assertEquals(4 + 63 + 64, InventorySpace.fits(slots, 64));
    }

    @Test
    void overfullStacksNeverCountNegative() {
        List<InventorySpace.Slot> slots = new ArrayList<>(Collections.nCopies(36, InventorySpace.Slot.other()));
        slots.set(0, InventorySpace.Slot.same(99)); // a stacked/admin stack above the max
        assertEquals(0, InventorySpace.fits(slots, 64));
    }

    @Test
    void badMaxStackFitsNothing() {
        assertEquals(0, InventorySpace.fits(empty(), 0));
    }
}

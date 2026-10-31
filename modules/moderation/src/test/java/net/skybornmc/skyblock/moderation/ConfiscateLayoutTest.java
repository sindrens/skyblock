package net.skybornmc.skyblock.moderation;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** QOL3: the confiscate screen in the standard frame, every place on a fixed slot. */
class ConfiscateLayoutTest {

    private static final Set<Integer> CONTENT = new HashSet<>();

    static {
        for (int slot : ConfiscateLayout.CONTENT) {
            CONTENT.add(slot);
        }
    }

    /** Every player-inventory slot (hotbar, backpack, armor, off hand) is shown exactly once over the two tabs. */
    @Test
    void inventoryTabsCoverEverySlotOnce() {
        Set<Integer> seen = new HashSet<>();
        for (ConfiscateLayout.View view : new ConfiscateLayout.View[]{ConfiscateLayout.View.HOTBAR, ConfiscateLayout.View.BACKPACK}) {
            int[] map = ConfiscateLayout.slots(view, 27);
            for (int menuSlot = 0; menuSlot < map.length; menuSlot++) {
                if (map[menuSlot] >= 0) {
                    assertTrue(CONTENT.contains(menuSlot), view + " uses frame slot " + menuSlot);
                    assertTrue(seen.add(map[menuSlot]), "inventory slot " + map[menuSlot] + " shown twice");
                }
            }
        }
        assertEquals(41, seen.size());
        for (int i = 0; i <= 40; i++) {
            assertTrue(seen.contains(i), "inventory slot " + i + " missing");
        }
    }

    @Test
    void enderTabStaysInsideTheFrame() {
        int[] map = ConfiscateLayout.slots(ConfiscateLayout.View.ENDER, 27);
        int count = 0;
        for (int menuSlot = 0; menuSlot < map.length; menuSlot++) {
            if (map[menuSlot] >= 0) {
                assertTrue(CONTENT.contains(menuSlot));
                count++;
            }
        }
        assertEquals(27, count);
        // a bigger ender chest never spills into the frame
        int big = 0;
        for (int at : ConfiscateLayout.slots(ConfiscateLayout.View.ENDER, 54)) {
            if (at >= 0) {
                big++;
            }
        }
        assertEquals(ConfiscateLayout.CONTENT.length, big);
    }

    @Test
    void armorHasItsOwnRow() {
        int[] map = ConfiscateLayout.slots(ConfiscateLayout.View.HOTBAR, 27);
        assertEquals(ConfiscateLayout.HELMET, map[37]);
        assertEquals(ConfiscateLayout.BOOTS, map[40]);
        assertEquals(ConfiscateLayout.OFF_HAND, map[42]);
        assertEquals(0, map[10]);
        assertEquals(8, map[20]);
    }

    @Test
    void placesAreNamed() {
        assertEquals(new ConfiscateLayout.Place("hotbar", 3), ConfiscateLayout.place(ConfiscateLayout.View.HOTBAR, 2));
        assertEquals(new ConfiscateLayout.Place("backpack", 1), ConfiscateLayout.place(ConfiscateLayout.View.BACKPACK, 9));
        assertEquals(new ConfiscateLayout.Place("helmet", 0), ConfiscateLayout.place(ConfiscateLayout.View.HOTBAR, 39));
        assertEquals(new ConfiscateLayout.Place("off-hand", 0), ConfiscateLayout.place(ConfiscateLayout.View.HOTBAR, 40));
        assertEquals(new ConfiscateLayout.Place("ender", 5), ConfiscateLayout.place(ConfiscateLayout.View.ENDER, 4));
        assertEquals(ConfiscateLayout.View.ENDER.source(), Sanctions.Source.ENDER);
    }
}

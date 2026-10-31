package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.menu.MenuLayout;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-BUGS: the calendar days stay inside the frame (no border column, no bottom bar). */
class DailyMenuLayoutTest {

    @Test
    void daySlotsAreTheContentAreaOnly() {
        int[] slots = DailyMenu.daySlots();
        assertEquals(28, slots.length);
        Set<Integer> seen = new HashSet<>();
        for (int slot : slots) {
            assertTrue(seen.add(slot), "slot " + slot + " twice");
            int row = slot / 9;
            int col = slot % 9;
            assertTrue(row >= 1 && row <= 4, "slot " + slot + " is outside rows 1-4");
            assertTrue(col >= 1 && col <= 7, "slot " + slot + " is on a border column");
            assertFalse(MenuLayout.standard(6, slot));
        }
        assertFalse(seen.contains(DailyMenu.SLOT_STREAK));
        assertFalse(MenuLayout.standard(6, DailyMenu.SLOT_STREAK), "the streak sits on a free bar slot (46)");
    }

    @Test
    void theWindowAlwaysHoldsTheNextDay() {
        assertEquals(1, DailyMenu.firstShownDay(30, 1, 28));
        assertEquals(1, DailyMenu.firstShownDay(30, 28, 28));
        assertEquals(3, DailyMenu.firstShownDay(30, 29, 28));
        assertEquals(3, DailyMenu.firstShownDay(30, 30, 28));
        assertEquals(1, DailyMenu.firstShownDay(11, 11, 28), "a short calendar shows from day 1");
        for (int next = 1; next <= 30; next++) {
            int first = DailyMenu.firstShownDay(30, next, 28);
            assertTrue(next >= first && next < first + 28, "day " + next + " must be visible");
        }
    }
}

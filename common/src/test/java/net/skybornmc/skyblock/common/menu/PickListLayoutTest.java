package net.skybornmc.skyblock.common.menu;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickListLayoutTest {

    @Test
    void rowsGrowWithTheChoices() {
        assertEquals(3, PickListLayout.rows(0));
        assertEquals(3, PickListLayout.rows(1));
        assertEquals(3, PickListLayout.rows(7));
        assertEquals(4, PickListLayout.rows(8));
        assertEquals(4, PickListLayout.rows(14));
        assertEquals(6, PickListLayout.rows(15), "5-row menus are not used (I9)");
        assertEquals(6, PickListLayout.rows(21));
        assertEquals(6, PickListLayout.rows(22));
        assertEquals(6, PickListLayout.rows(500));
    }

    @Test
    void perPageIsEverythingUntilTheListPages() {
        assertEquals(9, PickListLayout.perPage(9));
        assertEquals(14, PickListLayout.perPage(14));
        assertEquals(28, PickListLayout.perPage(15));
        assertEquals(28, PickListLayout.perPage(22));
        assertEquals(28, PickListLayout.perPage(100));
    }

    @Test
    void shortRowsAreCentred() {
        assertEquals(13, PickListLayout.slot(0, 1));
        assertEquals(12, PickListLayout.slot(0, 3));
        assertEquals(14, PickListLayout.slot(2, 3));
        assertEquals(11, PickListLayout.slot(0, 4)); // (7-4)/2 = 1 → 11..14
        assertEquals(10, PickListLayout.slot(0, 7));
        assertEquals(16, PickListLayout.slot(6, 7));
        // 9 choices: 4 rows, Centered's split 5 + 4 (row 1: columns 0 2 4 6 8, row 2: 1 3 5 7)
        assertEquals(9, PickListLayout.slot(0, 9));
        assertEquals(17, PickListLayout.slot(4, 9));
        assertEquals(19, PickListLayout.slot(5, 9));
        assertEquals(21, PickListLayout.slot(6, 9));
        assertEquals(25, PickListLayout.slot(8, 9));
        // a 6-row page fills the 7x4 grid from the left
        assertEquals(10, PickListLayout.slot(0, 20, 6));
        assertEquals(19, PickListLayout.slot(7, 20, 6));
    }

    @Test
    void slotsStayInsideTheFrameAndNeverRepeat() {
        for (int shown = 1; shown <= PickListLayout.PER_PAGE; shown++) {
            Set<Integer> used = new HashSet<>();
            int rows = PickListLayout.rows(shown);
            for (int i = 0; i < shown; i++) {
                int slot = PickListLayout.slot(i, shown);
                int col = slot % 9;
                int row = slot / 9;
                assertTrue(col >= 0 && col <= 8, "column of " + slot);
                assertTrue(row >= 1 && row < rows - 1, "row of " + slot + " in " + rows + " rows");
                assertFalse(MenuLayout.standard(rows, slot));
                assertTrue(used.add(slot), "slot " + slot + " twice");
            }
        }
    }

    @Test
    void badIndexesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PickListLayout.slot(-1, 3));
        assertThrows(IllegalArgumentException.class, () -> PickListLayout.slot(3, 3));
        assertThrows(IllegalArgumentException.class, () -> PickListLayout.slot(0, 29));
    }
}

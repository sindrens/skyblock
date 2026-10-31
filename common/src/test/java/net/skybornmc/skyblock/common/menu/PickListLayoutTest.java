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
        assertEquals(5, PickListLayout.rows(15));
        assertEquals(5, PickListLayout.rows(21));
        assertEquals(6, PickListLayout.rows(22));
        assertEquals(6, PickListLayout.rows(500));
    }

    @Test
    void perPageIsEverythingUntilTheListPages() {
        assertEquals(9, PickListLayout.perPage(9));
        assertEquals(21, PickListLayout.perPage(21));
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
        // 9 choices: full first row, the last two centred on row 2
        assertEquals(16, PickListLayout.slot(6, 9));
        assertEquals(21, PickListLayout.slot(7, 9));
        assertEquals(22, PickListLayout.slot(8, 9));
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
                assertTrue(col >= 1 && col <= 7, "column of " + slot);
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

package net.skybornmc.skyblock.common.menu;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plan 4 Nov C §3: airy menus (at most 7 per row, centred, an empty row between rows) and Help on one slot. */
class CenteredAiryTest {

    @Test
    void packedRowsAreCentredWithoutHoles() {
        assertArrayEquals(new int[]{4}, Centered.packed(1));
        assertArrayEquals(new int[]{2, 3, 4, 5, 6}, Centered.packed(5));
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7}, Centered.packed(7));
        assertThrows(IllegalArgumentException.class, () -> Centered.packed(8));
        assertArrayEquals(new int[]{1, 3, 5, 7}, Centered.airyColumns(4), "up to 4 keep a gap");
    }

    @Test
    void airyLayouts() {
        Centered.Layout four = Centered.airy(4);
        assertEquals(3, four.rows());
        assertArrayEquals(new int[]{10, 12, 14, 16}, four.slots());
        Centered.Layout nine = Centered.airy(9);
        assertEquals(6, nine.rows());
        assertArrayEquals(new int[]{11, 12, 13, 14, 15, 28, 30, 32, 34}, nine.slots(), "5 on row 1, row 2 empty, 4 on row 3");
        assertEquals(0, Centered.airy(0).slots().length);
        for (int n = 0; n <= Centered.MAX_SMALL; n++) {
            Centered.Layout l = Centered.airy(n);
            Set<Integer> rows = new HashSet<>();
            Set<Integer> used = new HashSet<>();
            for (int s : l.slots()) {
                assertTrue(used.add(s));
                assertTrue(s % 9 >= 1 && s % 9 <= 7, n + ": inside columns 1-7, got " + s);
                assertTrue(s / 9 < l.rows() - 1, n + ": above the bottom row");
                rows.add(s / 9);
            }
            assertTrue(!rows.contains(2), n + ": row 2 stays empty");
            assertEquals(n, l.slots().length);
        }
        assertThrows(IllegalArgumentException.class, () -> Centered.airy(15));
    }

    @Test
    void helpIsRightOfClose() {
        assertEquals(50, MenuLayout.help(6));
        assertEquals(32, MenuLayout.help(4));
        assertEquals(23, MenuLayout.help(3));
        assertEquals(MenuLayout.NONE, MenuLayout.help(2));
        for (int rows : new int[]{3, 4, 6}) {
            assertEquals(MenuLayout.close(rows) + 1, MenuLayout.help(rows));
            assertTrue(!MenuLayout.standard(rows, MenuLayout.help(rows)));
        }
    }
}

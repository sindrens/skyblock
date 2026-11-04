package net.skybornmc.skyblock.guide;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plan 4 Nov C §2: Kindle's groups are centred rows with air between the buttons, never on the bottom bar. */
class GuideLayoutTest {

    @Test
    void groupsAreCentredWithGaps() {
        assertArrayEquals(new int[]{13}, GuideLayout.row(1, 1));
        assertArrayEquals(new int[]{11, 13, 15}, GuideLayout.row(1, 3), "quests, next step, finds");
        assertArrayEquals(new int[]{28, 30, 32, 34}, GuideLayout.row(3, 4), "level, upgrades, tasks, milestones");
        assertArrayEquals(new int[]{38, 40, 42}, GuideLayout.row(4, 3), "tools, farm, mine");
        assertArrayEquals(new int[]{39, 41}, GuideLayout.row(4, 2), "a missing module re-centres the row");
        assertEquals(0, GuideLayout.row(4, 0).length);
    }

    @Test
    void neverMoreThanFourAndNeverTwoInTouchingSlots() {
        for (int n = 0; n <= 6; n++) {
            int[] slots = GuideLayout.row(3, n);
            assertTrue(slots.length <= GuideLayout.MAX_PER_ROW);
            Set<Integer> seen = new HashSet<>();
            for (int s : slots) {
                assertTrue(seen.add(s));
                assertTrue(!seen.contains(s - 1) && !seen.contains(s + 1), "gap between buttons: " + s);
                assertTrue(s >= 27 && s <= 35);
            }
        }
    }

    @Test
    void theRowsStayAboveTheBottomBarAndAwayFromKindle() {
        assertThrows(IllegalArgumentException.class, () -> GuideLayout.row(5, 1));
        assertThrows(IllegalArgumentException.class, () -> GuideLayout.row(0, 1));
        for (int row : new int[]{GuideMenu.ROW_KINDLE, GuideMenu.ROW_ISLAND, GuideMenu.ROW_GRIND}) {
            for (int s : GuideLayout.row(row, 4)) {
                assertTrue(s != GuideMenu.SLOT_KINDLE && s < 45);
            }
        }
    }

    @Test
    void progressDots() {
        assertEquals(0, GuideRules.filled(0, 10));
        assertEquals(4, GuideRules.filled(46, 10));
        assertEquals(10, GuideRules.filled(100, 10));
        assertEquals(10, GuideRules.filled(250, 10));
        assertEquals(0, GuideRules.filled(-5, 10));
        assertEquals(0, GuideRules.filled(50, 0));
        assertEquals(46, GuideRules.percent("46"));
        assertEquals(46, GuideRules.percent("46.9%"));
        assertEquals(0, GuideRules.percent("-"));
        assertEquals(0, GuideRules.percent(null));
        assertEquals(100, GuideRules.percent("130"));
    }
}

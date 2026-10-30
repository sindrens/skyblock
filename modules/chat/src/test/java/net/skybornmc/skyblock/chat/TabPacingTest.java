package net.skybornmc.skyblock.chat;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF4: the tab list checks each player once per update-ticks, not every tick. */
class TabPacingTest {

    private static int checksOver(int online, int updateTicks, int ticks) {
        TabPacing pacing = new TabPacing();
        int total = 0;
        for (int t = 0; t < ticks; t++) {
            int n = pacing.next(online, updateTicks);
            assertTrue(n >= 0 && n <= online);
            total += n;
        }
        return total;
    }

    @Test
    void lonePlayerIsCheckedOncePerInterval() {
        assertEquals(1, checksOver(1, 40, 40));
        assertEquals(3, checksOver(1, 40, 120));
        assertEquals(0, checksOver(1, 40, 39));
    }

    @Test
    void manyPlayersAreSpreadEvenly() {
        TabPacing pacing = new TabPacing();
        int max = 0;
        int total = 0;
        for (int t = 0; t < 40; t++) {
            int n = pacing.next(300, 40);
            max = Math.max(max, n);
            total += n;
        }
        assertEquals(300, total, "every player once per 40 ticks");
        assertTrue(max <= 8, "7 or 8 per tick, never a burst");
    }

    @Test
    void noPlayersResetsTheCarry() {
        TabPacing pacing = new TabPacing();
        for (int t = 0; t < 39; t++) {
            pacing.next(1, 40);
        }
        assertEquals(0, pacing.next(0, 40));
        assertEquals(0, pacing.next(1, 40), "carry was reset while empty");
    }

    @Test
    void shortIntervalNeverExceedsOnline() {
        assertEquals(10 * 5, checksOver(5, 1, 10));
        assertEquals(10 * 5, checksOver(5, 0, 10), "a zero interval is treated as 1");
    }

    @Test
    void fingerprintChangesWithEveryPart() {
        Display.RankView rank = new Display.RankView("&cADMIN ", NamedTextColor.RED, 900);
        String base = Display.key(rank, "", false, "", "");
        assertEquals(base, Display.key(new Display.RankView("&cADMIN ", NamedTextColor.RED, 900), "", false, "", ""));
        assertNotEquals(base, Display.key(new Display.RankView("&aVIP ", NamedTextColor.RED, 900), "", false, "", ""));
        assertNotEquals(base, Display.key(new Display.RankView("&cADMIN ", NamedTextColor.GREEN, 900), "", false, "", ""));
        assertNotEquals(base, Display.key(new Display.RankView("&cADMIN ", NamedTextColor.RED, 100), "", false, "", ""));
        assertNotEquals(base, Display.key(rank, "Nick1", false, "", ""));
        assertNotEquals(base, Display.key(rank, "", true, "", ""));
        assertNotEquals(base, Display.key(rank, "", false, "Hero", ""));
        assertNotEquals(base, Display.key(rank, "", false, "", "PRO"));
    }

    @Test
    void rankViewDefaults() {
        Display.RankView none = new Display.RankView(null, null, 0);
        assertEquals("", none.prefix());
        assertEquals(Display.DEFAULT_NAME, none.color());
        assertEquals(NamedTextColor.nearestTo(TextColor.color(0xC9C9C9)), Display.teamColor(none));
    }
}

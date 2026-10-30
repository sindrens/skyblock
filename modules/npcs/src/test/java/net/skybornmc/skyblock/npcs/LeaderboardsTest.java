package net.skybornmc.skyblock.npcs;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LeaderboardsTest {

    @Test
    void topThreeHaveOwnColors() {
        assertEquals("leaderboard.top1", Leaderboards.rankKey(1));
        assertEquals("leaderboard.top2", Leaderboards.rankKey(2));
        assertEquals("leaderboard.top3", Leaderboards.rankKey(3));
        assertEquals("leaderboard.place", Leaderboards.rankKey(4));
        assertEquals("leaderboard.place", Leaderboards.rankKey(10));
    }

    @Test
    void alwaysExactlySizeLines() {
        List<Leaderboards.Row> rows = List.of(new Leaderboards.Row("Steve", Component.text("1")),
                new Leaderboards.Row("Alex", Component.text("2")), new Leaderboards.Row("Notch", Component.text("3")),
                new Leaderboards.Row("Jeb", Component.text("4")));
        List<Leaderboards.Line> lines = Leaderboards.lines(rows, 10);
        assertEquals(10, lines.size());
        assertEquals("leaderboard.top1", lines.get(0).key());
        assertEquals("Steve", lines.get(0).row().player());
        assertEquals("leaderboard.place", lines.get(3).key());
        assertEquals(4, lines.get(3).place());
        assertEquals("leaderboard.empty-place", lines.get(4).key());
        assertEquals(5, lines.get(4).place());
        assertNull(lines.get(9).row());

        assertEquals(3, Leaderboards.lines(rows, 3).size(), "longer lists are cut");
        assertEquals("leaderboard.empty-place", Leaderboards.lines(null, 3).get(0).key(), "not read yet = empty places");
    }

    @Test
    void displayNames() {
        assertEquals("Steve", Leaderboards.displayName(" Steve ", "?"));
        assertEquals("?", Leaderboards.displayName(null, "?"));
        assertEquals("?", Leaderboards.displayName("  ", "?"));
        assertEquals(16, Leaderboards.displayName("A_very_long_island_name", "?").length());
    }
}

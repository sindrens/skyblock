package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.IslandTop;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Island boards (PHASE-5 M5.2): order, ties, solo/team, one island per player. */
class TopBoardTest {

    private static TopBoard.Candidate c(String name, UUID account, boolean team, int level, double points, long reached, double worth) {
        return new TopBoard.Candidate(UUID.nameUUIDFromBytes(name.getBytes()), UUID.randomUUID(), account, name, team, level, points,
                reached, worth);
    }

    @Test
    void levelThenPointsThenFirstToReach() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID(), p4 = UUID.randomUUID();
        List<TopBoard.Candidate> all = List.of(
                c("late", p1, false, 38, 385, 200, 0),
                c("early", p2, false, 38, 385, 100, 0),
                c("morePoints", p3, false, 38, 389, 300, 0),
                c("top", p4, false, 40, 400, 999, 0));
        List<IslandTop.Entry> board = TopBoard.rank(all, IslandTop.Kind.LEVEL, IslandTop.Board.SOLO);
        assertEquals(List.of("top", "morePoints", "early", "late"), board.stream().map(IslandTop.Entry::name).toList());
        assertEquals(List.of(1, 2, 3, 4), board.stream().map(IslandTop.Entry::place).toList());
    }

    @Test
    void soloAndTeamAreSeparate() {
        List<TopBoard.Candidate> all = List.of(c("solo", UUID.randomUUID(), false, 10, 100, 0, 0),
                c("team", UUID.randomUUID(), true, 50, 500, 0, 0));
        assertEquals(List.of("solo"), TopBoard.rank(all, IslandTop.Kind.LEVEL, IslandTop.Board.SOLO).stream().map(IslandTop.Entry::name).toList());
        assertEquals(List.of("team"), TopBoard.rank(all, IslandTop.Kind.LEVEL, IslandTop.Board.TEAM).stream().map(IslandTop.Entry::name).toList());
        assertEquals(2, TopBoard.rank(all, IslandTop.Kind.LEVEL, IslandTop.Board.ALL).size());
    }

    @Test
    void onePlayerOneIslandBestCounts() {
        UUID player = UUID.randomUUID();
        List<TopBoard.Candidate> all = List.of(c("profile2", player, false, 5, 50, 0, 0),
                c("profile1", player, false, 20, 200, 0, 0), c("other", UUID.randomUUID(), false, 10, 100, 0, 0));
        List<IslandTop.Entry> board = TopBoard.rank(all, IslandTop.Kind.LEVEL, IslandTop.Board.SOLO);
        assertEquals(List.of("profile1", "other"), board.stream().map(IslandTop.Entry::name).toList());
        assertEquals(2, board.get(1).place(), "places stay gapless");
    }

    @Test
    void valueBoardRanksBlockWorthAndSkipsEmpty() {
        List<TopBoard.Candidate> all = List.of(c("rich", UUID.randomUUID(), false, 1, 10, 0, 5_000_000),
                c("poor", UUID.randomUUID(), false, 30, 300, 0, 100), c("none", UUID.randomUUID(), false, 30, 300, 0, 0),
                c("zero", UUID.randomUUID(), false, 0, 0, 0, 0));
        assertEquals(List.of("rich", "poor"), TopBoard.rank(all, IslandTop.Kind.VALUE, IslandTop.Board.SOLO).stream().map(IslandTop.Entry::name).toList());
        assertEquals(3, TopBoard.rank(all, IslandTop.Kind.LEVEL, IslandTop.Board.SOLO).size(), "islands without points are not ranked");
    }

    @Test
    void ownPlaceComesFromTheIslandsOwnBoard() {
        TopBoard.Candidate solo = c("solo", UUID.randomUUID(), false, 10, 100, 0, 0);
        TopBoard.Candidate team = c("team", UUID.randomUUID(), true, 5, 50, 0, 0);
        List<TopBoard.Candidate> all = List.of(solo, team);
        Map<UUID, Integer> places = TopBoard.ownPlaces(TopBoard.rank(all, IslandTop.Kind.LEVEL, IslandTop.Board.SOLO),
                TopBoard.rank(all, IslandTop.Kind.LEVEL, IslandTop.Board.TEAM));
        assertEquals(1, places.get(solo.islandId()));
        assertEquals(1, places.get(team.islandId()));
    }
}

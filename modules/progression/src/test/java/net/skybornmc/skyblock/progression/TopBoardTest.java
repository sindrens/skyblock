package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.IslandTop;
import net.skybornmc.skyblock.common.progression.Pillar;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Island boards (PHASE-5 M5.2, M5.7): order, ties, solo/team, one island per player, weekly and pillar boards, hidden staff. */
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

    private static TopBoard.Candidate w(String name, boolean team, double earnedGain, int pillarGain, int level, int mining,
                                        double miningXp, boolean hidden) {
        int[] levels = new int[Pillar.values().length];
        double[] xp = new double[Pillar.values().length];
        levels[Pillar.MINING.ordinal()] = mining;
        xp[Pillar.MINING.ordinal()] = miningXp;
        return new TopBoard.Candidate(UUID.nameUUIDFromBytes(name.getBytes()), UUID.randomUUID(), UUID.randomUUID(), name, team, level,
                level * 10.0, 0, 0, earnedGain, pillarGain, levels, xp, "Leader" + name, hidden);
    }

    private static List<String> names(List<IslandTop.Entry> board) {
        return board.stream().map(IslandTop.Entry::name).toList();
    }

    @Test
    void weeklyGainRanksEarnedPointsGainedThenPillarLevels() {
        List<TopBoard.Candidate> all = List.of(w("big", false, 40, 5, 10, 0, 0, false), w("tieMorePillars", false, 25, 9, 1, 0, 0, false),
                w("tieFewerPillars", false, 25, 3, 50, 0, 0, false), w("nothing", false, 0, 0, 90, 0, 0, false));
        List<IslandTop.Entry> board = TopBoard.rank(all, IslandTop.Kind.WEEKLY_GAIN, IslandTop.Board.SOLO);
        assertEquals(List.of("big", "tieMorePillars", "tieFewerPillars"), names(board), "no gain = not ranked, even at level 90");
        assertEquals(40, board.getFirst().value());
        assertEquals("Leaderbig", board.getFirst().leader());
    }

    @Test
    void weeklyPillarsRanksPillarLevelsGained() {
        List<TopBoard.Candidate> all = List.of(w("a", true, 100, 2, 10, 0, 0, false), w("b", true, 1, 7, 10, 0, 0, false),
                w("c", false, 50, 50, 10, 0, 0, false));
        List<IslandTop.Entry> board = TopBoard.rank(all, IslandTop.Kind.WEEKLY_PILLARS, IslandTop.Board.TEAM);
        assertEquals(List.of("b", "a"), names(board));
        assertEquals(7, board.getFirst().value());
    }

    @Test
    void pillarBoardRanksLevelThenXp() {
        List<TopBoard.Candidate> all = List.of(w("lowXp", false, 0, 0, 1, 20, 100, false), w("highXp", false, 0, 0, 1, 20, 900, false),
                w("best", false, 0, 0, 1, 30, 0, false), w("zero", false, 0, 0, 50, 0, 0, false));
        List<IslandTop.Entry> board = TopBoard.rankPillar(all, Pillar.MINING, IslandTop.Board.SOLO);
        assertEquals(List.of("best", "highXp", "lowXp"), names(board));
        assertEquals(30, board.getFirst().value());
        assertTrue(TopBoard.rankPillar(all, Pillar.COMBAT, IslandTop.Board.SOLO).isEmpty());
    }

    @Test
    void hiddenStaffIslandsAreNeverRanked() {
        List<TopBoard.Candidate> all = List.of(w("staff", false, 99, 9, 99, 99, 0, true), w("player", false, 1, 1, 1, 1, 0, false));
        for (IslandTop.Kind kind : IslandTop.Kind.values()) {
            assertTrue(!names(TopBoard.rank(all, kind, IslandTop.Board.ALL)).contains("staff"), kind.name());
        }
        assertEquals(List.of("player"), names(TopBoard.rankPillar(all, Pillar.MINING, IslandTop.Board.SOLO)));
    }

    @Test
    void fullTiesAreBrokenByIslandIdTheSameWayEveryTime() {
        TopBoard.Candidate a = c("a", UUID.randomUUID(), false, 10, 100, 5, 0);
        TopBoard.Candidate b = c("b", UUID.randomUUID(), false, 10, 100, 5, 0);
        List<String> expected = a.islandId().compareTo(b.islandId()) < 0 ? List.of("a", "b") : List.of("b", "a");
        assertEquals(expected, names(TopBoard.rank(List.of(a, b), IslandTop.Kind.LEVEL, IslandTop.Board.SOLO)));
        assertEquals(expected, names(TopBoard.rank(List.of(b, a), IslandTop.Kind.LEVEL, IslandTop.Board.SOLO)));
    }

    @Test
    void gainNeverNegative() {
        assertEquals(0, TopBoard.gain(10, 12));
        assertEquals(2.5, TopBoard.gain(12.5, 10));
    }

    @Test
    void snapshotHoldsEveryBoardAndOwnPlaces() {
        TopBoard.Candidate solo = w("solo", false, 5, 1, 10, 3, 0, false);
        TopBoard.Candidate team = w("team", true, 9, 2, 20, 0, 0, false);
        TopBoards.Snapshot snap = TopBoards.rank(List.of(solo, team), 42L);
        assertEquals(42L, snap.rankedAt());
        for (IslandTop.Kind kind : IslandTop.Kind.values()) {
            assertEquals(3, snap.kinds().get(kind).size(), kind.name());
        }
        assertEquals(List.of("solo"), names(snap.pillars().get(Pillar.MINING).get(IslandTop.Board.ALL)));
        assertEquals(1, snap.places().get(solo.islandId()));
        assertEquals(1, snap.places().get(team.islandId()));
        assertEquals(9, snap.islands().get(team.islandId()).earnedGain());
    }
}

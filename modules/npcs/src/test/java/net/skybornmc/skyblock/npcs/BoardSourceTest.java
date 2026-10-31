package net.skybornmc.skyblock.npcs;

import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService;
import net.skybornmc.skyblock.common.progression.IslandTop;
import net.skybornmc.skyblock.common.skills.SkillType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardSourceTest {

    @Test
    void parsesEveryKind() {
        assertEquals(Optional.of(new BoardSource.Money(Currency.MONEY)), BoardSource.parse("money"));
        assertEquals(Optional.of(new BoardSource.Money(Currency.CRYSTALS)), BoardSource.parse("tokens"));
        assertEquals(Optional.of(new BoardSource.Skill(SkillType.MINING)), BoardSource.parse(" Skill  MINING "));
        assertEquals(Optional.of(new BoardSource.Island(IslandStat.MONEY_EARNED, IslandStatsService.Period.ALL_TIME,
                IslandStatsService.Board.ALL)), BoardSource.parse("island money_earned"));
        assertEquals(Optional.of(new BoardSource.Island(IslandStat.BLOCKS_MINED, IslandStatsService.Period.THIS_WEEK,
                IslandStatsService.Board.SOLO)), BoardSource.parse("island blocks-mined this_week solo"));
        assertInstanceOf(BoardSource.None.class, BoardSource.parse("none").orElseThrow());
    }

    @Test
    void rejectsBroken() {
        for (String bad : new String[]{"", "skill", "skill cooking", "island", "island nope", "island money_earned yearly",
                "island money_earned all_time both", "money extra", "diamonds", "none please",
                "island_level both", "island_level solo extra", "island_value nope"}) {
            assertTrue(BoardSource.parse(bad).isEmpty(), bad);
        }
    }

    @Test
    void islandLevelSources() {
        assertEquals(Optional.of(new BoardSource.IslandLevel(IslandTop.Kind.LEVEL, IslandTop.Board.ALL)), BoardSource.parse("island_level"));
        assertEquals(Optional.of(new BoardSource.IslandLevel(IslandTop.Kind.LEVEL, IslandTop.Board.TEAM)), BoardSource.parse("Island_Level TEAM"));
        assertEquals(Optional.of(new BoardSource.IslandLevel(IslandTop.Kind.VALUE, IslandTop.Board.SOLO)), BoardSource.parse("island_value solo"));
        assertEquals("island_level:solo", BoardSource.parse("island_level solo").orElseThrow().key());
    }

    @Test
    void oldDefaultSourcesMigrateOnlyWhenUntouched() {
        var change = new NpcsConfig().valueChanges().get(2).get("leaderboards.sources");
        assertTrue(change.matches(new ArrayList<>(NpcsConfig.SOURCES_V1)));
        assertFalse(change.matches(List.of("island_top_solo: none")));
        assertEquals(new NpcsConfig().sources, change.to());
    }

    @Test
    void sameDataSameKey() {
        assertEquals(BoardSource.parse("money").orElseThrow().key(), BoardSource.parse("MONEY").orElseThrow().key());
        assertEquals("skill:combat", BoardSource.parse("skill combat").orElseThrow().key());
    }

    @Test
    void defaultsParseAndBrokenBecomeComingSoon() {
        List<String> problems = new ArrayList<>();
        Map<String, BoardSource> all = BoardSource.parseAll(new NpcsConfig().sources, problems);
        assertEquals(List.of(), problems);
        assertEquals(new BoardSource.Skill(SkillType.FISHING), all.get("fishing"));
        assertEquals(new BoardSource.IslandLevel(IslandTop.Kind.LEVEL, IslandTop.Board.SOLO), all.get("island_top_solo"));
        assertEquals(new BoardSource.IslandLevel(IslandTop.Kind.LEVEL, IslandTop.Board.TEAM), all.get("island_top_team"));
        assertEquals(new BoardSource.IslandWeekly(IslandTop.Kind.WEEKLY_GAIN, IslandTop.Board.SOLO, true), all.get("weekly_island_gain"));
        assertEquals(new BoardSource.IslandWeekly(IslandTop.Kind.WEEKLY_PILLARS, IslandTop.Board.SOLO, true), all.get("weekly_pillar_gain"));
        assertInstanceOf(BoardSource.None.class, all.get("boss_damage"));
        assertEquals(new BoardSource.Money(Currency.MONEY), all.get("baltop"));

        Map<String, BoardSource> broken = BoardSource.parseAll(List.of("a: skill cooking", "nope"), problems);
        assertInstanceOf(BoardSource.None.class, broken.get("a"));
        assertEquals(2, problems.size());
    }

    @Test
    void weeklyBoards() {
        assertEquals(Optional.of(new BoardSource.IslandWeekly(IslandTop.Kind.WEEKLY_GAIN, IslandTop.Board.ALL, false)),
                BoardSource.parse("island_gain"));
        assertEquals(Optional.of(new BoardSource.IslandWeekly(IslandTop.Kind.WEEKLY_GAIN, IslandTop.Board.TEAM, false)),
                BoardSource.parse("island_gain this_week team"));
        assertEquals(Optional.of(new BoardSource.IslandWeekly(IslandTop.Kind.WEEKLY_PILLARS, IslandTop.Board.SOLO, false)),
                BoardSource.parse("Pillar_Gain solo"));
        assertEquals(Optional.of(new BoardSource.IslandWeekly(IslandTop.Kind.WEEKLY_PILLARS, IslandTop.Board.SOLO, true)),
                BoardSource.parse("pillar_gain this_week cycle"));
        assertEquals(Optional.empty(), BoardSource.parse("island_gain last_week"));
        assertEquals(Optional.empty(), BoardSource.parse("island_gain this_week solo team"));
        assertEquals("island_weekly_gain:cycle", BoardSource.parse("island_gain cycle").orElseThrow().key());
        assertEquals("island_weekly_pillars:team", BoardSource.parse("pillar_gain team").orElseThrow().key());
    }

    @Test
    void cycleSwitchesSoloAndTeamByTheClock() {
        assertEquals(IslandTop.Board.SOLO, IslandTop.cycleBoard(0, 10));
        assertEquals(IslandTop.Board.SOLO, IslandTop.cycleBoard(9_999, 10));
        assertEquals(IslandTop.Board.TEAM, IslandTop.cycleBoard(10_000, 10));
        assertEquals(IslandTop.Board.SOLO, IslandTop.cycleBoard(20_000, 10));
        assertEquals(0, Leaderboards.cycleIndex(IslandTop.Board.SOLO));
        assertEquals(1, Leaderboards.cycleIndex(IslandTop.Board.TEAM));
    }

    @Test
    void m57MigrationOnlyReplacesTheUntouchedOldDefault() {
        assertEquals(4, new NpcsConfig().version());
        assertEquals(NpcsConfig.SOURCES_V2.size(), new NpcsConfig().sources.size());
    }
}

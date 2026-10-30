package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC3/AC4 storage on H2 (MySQL mode): island tables, the season summary and the wipe that keeps history. */
class SeasonResetTest {

    @TempDir
    Path dir;
    private Database db;
    private final AchievementStore store = new AchievementStore();
    private final AchievementRegistry registry = Fixtures.registry();
    private final int[] perTier = new AchievementsConfig().perTier();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "achievements-reset-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("achievements-test"));
        db.start();
        new Migrations(db, Logger.getLogger("achievements-test")).migrate("achievements", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void islandRowsSaveLoadUnlockOnceAndDelete() {
        UUID island = UUID.randomUUID();
        db.blockingTransaction(c -> {
            store.saveIsland(c, island, List.of(new IslandProgress.Row(IslandMetric.LIKES, 3), new IslandProgress.Row(IslandMetric.ISLAND_LEVEL, 20)), 1L);
            store.saveIsland(c, island, List.of(new IslandProgress.Row(IslandMetric.LIKES, 2), new IslandProgress.Row(IslandMetric.ISLAND_LEVEL, 12)), 2L);
            return null;
        });
        assertTrue(db.<Boolean>blocking(c -> store.unlockIsland(c, island, "rising_island", 1, 1L)));
        assertFalse(db.<Boolean>blocking(c -> store.unlockIsland(c, island, "rising_island", 1, 2L)), "once per island");
        AchievementStore.IslandsLoaded loaded = db.blocking(store::loadIslands);
        assertEquals(Map.of(IslandMetric.LIKES, 5L, IslandMetric.ISLAND_LEVEL, 20L), loaded.values().get(island));
        assertEquals(Map.of("rising_island", 1), loaded.unlocks().get(island));
        assertEquals(3, (int) db.<Integer>blocking(c -> store.deleteIsland(c, island)));
        assertTrue(db.blocking(store::loadIslands).values().isEmpty());
    }

    @Test
    void summaryCountsPersonalAndIslandTiers() {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        UUID mate = UUID.randomUUID();
        UUID mateOwner = UUID.randomUUID();
        UUID island = UUID.randomUUID();
        List<SeasonSummary.UnlockRow> unlocks = List.of(
                new SeasonSummary.UnlockRow(profile, owner, "warrior", 1), new SeasonSummary.UnlockRow(profile, owner, "warrior", 2),
                new SeasonSummary.UnlockRow(profile, owner, "warrior", 3), new SeasonSummary.UnlockRow(profile, owner, "warrior", 4),
                new SeasonSummary.UnlockRow(profile, owner, "warrior", 5), new SeasonSummary.UnlockRow(profile, owner, "first_sale", 1));
        List<SeasonSummary.IslandUnlockRow> islandUnlocks = List.of(new SeasonSummary.IslandUnlockRow(island, "loved_isle", 1),
                new SeasonSummary.IslandUnlockRow(island, "loved_isle", 2), new SeasonSummary.IslandUnlockRow(island, "team_flame", 1));
        List<SeasonSummary.Entry> entries = SeasonSummary.compute(1, registry, perTier, unlocks, islandUnlocks,
                Map.of(profile, island, mate, island), Map.of(profile, owner, mate, mateOwner), Set.of());
        assertEquals(2, entries.size());
        SeasonSummary.Entry mine = entries.stream().filter(e -> e.profileKey().equals(profile)).findFirst().orElseThrow();
        assertEquals(6, mine.tiers());
        assertEquals(2, mine.completed());
        assertEquals(19 + 1, mine.points());
        assertEquals(2, mine.islandTiers(), "Team Flame doesn't count on a solo island");
        assertEquals(1 + 2 + 1, mine.islandPoints(), "points are every tier the island stored (they gave boosts all season)");
        assertEquals(List.of("warrior"), mine.titles());
        SeasonSummary.Entry theirs = entries.stream().filter(e -> e.profileKey().equals(mate)).findFirst().orElseThrow();
        assertEquals(0, theirs.tiers());
        assertEquals(2, theirs.islandTiers(), "a member with only island progress still gets a summary");
        assertEquals(mateOwner, theirs.owner());
    }

    @Test
    void resetWritesHistoryWipesProgressAndRefusesTwice() {
        UUID owner = UUID.randomUUID();
        UUID island = UUID.randomUUID();
        db.blockingTransaction(c -> {
            store.save(c, owner, List.of(new ProfileProgress.Row(Metric.ITEMS_SOLD, 10)), 1L);
            store.unlock(c, owner, "first_sale", 1, owner, 1L);
            store.saveIsland(c, island, List.of(new IslandProgress.Row(IslandMetric.LIKES, 10)), 1L);
            store.unlockIsland(c, island, "loved_isle", 1, 1L);
            return null;
        });
        int[] result = db.blockingTransaction(c -> {
            assertFalse(store.hasHistory(c, 1));
            List<SeasonSummary.Entry> entries = SeasonSummary.compute(1, registry, perTier, store.allUnlocks(c), store.allIslandUnlocks(c),
                    Map.of(owner, island), Map.of(owner, owner), Set.of());
            store.insertHistory(c, entries, 5L);
            return new int[]{entries.size(), store.wipeSeason(c)};
        });
        assertEquals(1, result[0]);
        assertEquals(4, result[1]);
        assertTrue(db.<Boolean>blocking(c -> store.hasHistory(c, 1)));
        AchievementStore.Loaded gone = db.blocking(c -> store.load(c, owner));
        assertTrue(gone.values().isEmpty() && gone.unlocks().isEmpty(), "progress starts from zero");
        assertTrue(db.blocking(store::loadIslands).unlocks().isEmpty(), "island progress too");
        List<SeasonSummary.Entry> history = db.blocking(c -> store.history(c, owner));
        assertEquals(1, history.size());
        assertEquals(1, history.getFirst().season());
        assertEquals(1, history.getFirst().tiers());
        assertEquals(1, history.getFirst().islandTiers());
        assertEquals(List.of(), history.getFirst().titles());
        assertTrue(db.<List<SeasonSummary.Entry>>blocking(c -> store.history(c, UUID.randomUUID())).isEmpty());
    }

    @Test
    void titlesTextIsCutAtACommaAndParsedBack() {
        SeasonSummary.Entry e = new SeasonSummary.Entry(1, UUID.randomUUID(), UUID.randomUUID(), 0, 0, 0, 0, 0,
                List.of("warrior", "prospector", "mogul"));
        assertEquals("warrior,prospector,mogul", e.titlesText(100));
        assertEquals("warrior,prospector", e.titlesText(20));
        assertEquals(List.of("warrior", "prospector"), SeasonSummary.Entry.parseTitles("warrior, prospector,"));
        assertEquals(List.of(), SeasonSummary.Entry.parseTitles(""));
    }
}

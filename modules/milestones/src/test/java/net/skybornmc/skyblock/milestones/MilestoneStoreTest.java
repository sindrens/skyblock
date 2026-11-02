package net.skybornmc.skyblock.milestones;

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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flush, discovery, tier completion and claims on H2 (MySQL mode, same SQL as MariaDB). */
class MilestoneStoreTest {

    @TempDir
    Path dir;
    private Database db;
    private final UUID island = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();
    private final UUID steve = UUID.randomUUID();
    private final MilestoneModel.Chain wheat = new MilestoneModel.Chain("wheat", "Wheat Reaper", "WHEAT", MilestoneModel.Menu.ISLAND,
            "farming", "harvest_crop", Set.of("WHEAT"), MilestoneModel.WorldFilter.ISLAND, new long[]{10, 20, 30}, Map.of(), 1);

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "milestones-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("milestones-test"));
        db.start();
        new Migrations(db, Logger.getLogger("milestones-test")).migrate("milestones", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private MilestoneStore.FlushResult flush(UUID actor, long amount) {
        ProgressBuffer b = new ProgressBuffer();
        b.add(ProgressBuffer.Scope.ISLAND, island, "wheat", actor, amount);
        return db.<MilestoneStore.FlushResult>blocking(c -> MilestoneStore.flush(c, b.drain(),
                id -> id.equals("wheat") ? Optional.of(wheat) : Optional.empty(), 1));
    }

    @Test
    void firstFlushDiscoversAndTiersCompleteOnce() {
        MilestoneStore.FlushResult first = flush(alex, 12);
        assertEquals(1, first.discovered().size());
        assertEquals(List.of(new MilestoneStore.Completed(ProgressBuffer.Scope.ISLAND, island, "wheat", 1)), first.completed());
        MilestoneStore.FlushResult second = flush(steve, 20);
        assertEquals(List.of(), second.discovered());
        assertEquals(2, second.completed().size(), "32 reaches tiers 2 and 3 at once");
        assertEquals(List.of(), flush(steve, 100).completed(), "maxed: nothing more");
        Integer share = db.<Integer>blocking(c -> MilestoneStore.contribution(c, island, "wheat", alex));
        assertEquals(9, share, "12 of 132");
    }

    @Test
    void anIslandTierIsClaimedOnceAndMarksTheLowerTiers() {
        flush(alex, 25);
        Boolean steveClaims = db.<Boolean>blocking(c -> MilestoneStore.claim(c, ProgressBuffer.Scope.ISLAND, island, "wheat", 2, steve, 5));
        Boolean alexToo = db.<Boolean>blocking(c -> MilestoneStore.claim(c, ProgressBuffer.Scope.ISLAND, island, "wheat", 2, alex, 6));
        Boolean notReady = db.<Boolean>blocking(c -> MilestoneStore.claim(c, ProgressBuffer.Scope.ISLAND, island, "wheat", 3, alex, 7));
        assertTrue(steveClaims);
        assertFalse(alexToo, "once per island");
        assertFalse(notReady, "tier 3 is not completed");
        Map<String, Integer> claimed = db.<Map<String, Integer>>blocking(c -> MilestoneStore.claimed(c, ProgressBuffer.Scope.ISLAND, island));
        assertEquals(Map.of("wheat", 2), claimed);
        List<MilestoneStore.Row> rows = db.<List<MilestoneStore.Row>>blocking(c -> MilestoneStore.rows(c, ProgressBuffer.Scope.ISLAND, island));
        assertEquals(List.of(new MilestoneStore.Row("wheat", 25, 0, 2, 2)), rows);
    }

    @Test
    void topCountsCompletedTiersPerIslandAndBestProfilePerPlayer() {
        UUID other = UUID.randomUUID();
        UUID alexAlt = UUID.randomUUID();
        db.blockingTransaction(c -> {
            net.skybornmc.skyblock.common.db.Sql.update(c, "CREATE TABLE IF NOT EXISTS players (uuid BINARY(16) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL)");
            net.skybornmc.skyblock.common.db.Sql.update(c, "INSERT INTO players (uuid, name) VALUES (?, ?)", alex, "Alex");
            String insert = "INSERT INTO milestone_tiers (scope_type, scope_id, chain_id, tier, completed_at) VALUES (?, ?, ?, ?, 1)";
            for (int t = 1; t <= 3; t++) {
                net.skybornmc.skyblock.common.db.Sql.update(c, insert, "ISLAND", island, "wheat", t);
            }
            net.skybornmc.skyblock.common.db.Sql.update(c, insert, "ISLAND", other, "wheat", 1);
            net.skybornmc.skyblock.common.db.Sql.update(c, insert, "PROFILE", alex, "coal", 1);
            net.skybornmc.skyblock.common.db.Sql.update(c, insert, "PROFILE", alexAlt, "coal", 1);
            net.skybornmc.skyblock.common.db.Sql.update(c, insert, "PROFILE", alexAlt, "coal", 2);
            net.skybornmc.skyblock.common.db.Sql.update(c, insert, "PROFILE", steve, "iron", 1);
            return null;
        });
        Map<UUID, UUID> owners = Map.of(alex, alex, alexAlt, alex, steve, steve);
        java.util.function.Function<Map<UUID, Long>, List<net.skybornmc.skyblock.common.profile.BestProfile.Entry>> best =
                values -> net.skybornmc.skyblock.common.profile.BestProfile.collapse(values, owners::get, k -> true);
        var islands = db.<List<net.skybornmc.skyblock.common.leaderboard.TopLine>>blocking(c -> MilestoneStore.top(c, true, 10, best));
        assertEquals(List.of(new net.skybornmc.skyblock.common.leaderboard.TopLine(island, null, 3),
                new net.skybornmc.skyblock.common.leaderboard.TopLine(other, null, 1)), islands);
        var players = db.<List<net.skybornmc.skyblock.common.leaderboard.TopLine>>blocking(c -> MilestoneStore.top(c, false, 10, best));
        assertEquals(List.of(new net.skybornmc.skyblock.common.leaderboard.TopLine(alex, "Alex", 2),
                new net.skybornmc.skyblock.common.leaderboard.TopLine(steve, null, 1)), players, "best profile counts once, unknown name stays null");
        assertEquals(1, db.<List<net.skybornmc.skyblock.common.leaderboard.TopLine>>blocking(c -> MilestoneStore.top(c, false, 1, best)).size());
        assertTrue(db.<List<net.skybornmc.skyblock.common.leaderboard.TopLine>>blocking(c -> MilestoneStore.top(c, true, 0, best)).isEmpty());
    }
}

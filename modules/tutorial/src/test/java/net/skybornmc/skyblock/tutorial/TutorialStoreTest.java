package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.tutorial.TutorialService.State;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tutorial tables on H2 in MySQL mode: legacy players marked finished once (TQ6), resume after a restart, steps saved
 * once, the finish guard pays once per account, the Starter pack once per account.
 */
class TutorialStoreTest {

    private static final Logger LOG = Logger.getLogger("tutorial-test");
    private static final TutorialFlow FLOW = TutorialFlow.parse(new TutorialConfig().steps);

    @TempDir
    Path dir;
    private Database db;
    private final TutorialStore store = new TutorialStore();

    private Database open(List<UUID> existingPlayers) throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "tutorial-test";
        config.poolSize = 4;
        Database d = new Database(config, dir.toFile(), LOG);
        d.start();
        new Migrations(d, LOG).migrate("core", Migrations.class.getClassLoader());
        for (UUID id : existingPlayers) {
            d.blocking(c -> Sql.update(c, "INSERT INTO players (uuid, name, name_lower, first_seen, last_seen) VALUES (?, ?, ?, 1, 1)",
                    id, "p" + id.toString().substring(0, 6), "p" + id.toString().substring(0, 6)));
        }
        new Migrations(d, LOG).migrate("tutorial", getClass().getClassLoader());
        db = d;
        return d;
    }

    @AfterEach
    void stop() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void playersFromBeforeTheTutorialAreMarkedFinishedOnce() throws Exception {
        UUID old = UUID.randomUUID();
        open(List.of(old));
        Progress p = db.blocking(c -> store.load(c, old)).orElseThrow();
        assertEquals(State.LEGACY, p.state);
        assertTrue(p.rewarded, "legacy players never get the reward, even after a staff reset");
        assertTrue(p.packGiven);
        // A player who joins later starts new.
        UUID fresh = UUID.randomUUID();
        Progress n = db.blocking(c -> store.loadOrCreate(c, fresh, 1));
        assertEquals(State.NEW, n.state);
        assertFalse(n.rewarded);
        // Running the migrations again changes nothing (they run once).
        new Migrations(db, LOG).migrate("tutorial", getClass().getClassLoader());
        assertEquals(State.NEW, db.blocking(c -> store.load(c, fresh)).orElseThrow().state);
    }

    @Test
    void progressSurvivesARestartAndResumes() throws Exception {
        open(List.of());
        UUID id = UUID.randomUUID();
        Progress p = db.blocking(c -> store.loadOrCreate(c, id, 1));
        StepRules.start(p, FLOW, 100);
        StepRules.done(p, FLOW, "portal", true, 200);
        StepRules.done(p, FLOW, "menu", true, 250);
        StepRules.count(p, FLOW, "mine", 9, 15, true);
        p.chest = "islands:10,101,-2";
        db.blocking(c -> {
            store.save(c, p);
            store.markDone(c, id, "portal", 200, false, null);
            store.markDone(c, id, "menu", 250, true, UUID.randomUUID());
            store.setCount(c, id, "mine", 9);
            return null;
        });
        Progress loaded = db.blocking(c -> store.load(c, id)).orElseThrow();
        assertEquals(State.ACTIVE, loaded.state);
        assertEquals("arrive", loaded.step);
        assertEquals(Long.valueOf(100), loaded.startedAt);
        assertTrue(loaded.isDone("portal"));
        assertTrue(loaded.done.get("menu"), "early flag kept");
        assertEquals(9, loaded.count("mine"));
        assertEquals("islands:10,101,-2", loaded.chest);
        assertFalse(StepRules.resume(loaded, FLOW, 1, 999));
        assertEquals("arrive", loaded.step);
    }

    @Test
    void aStepIsStoredOnceAndCountsAreUpserted() throws Exception {
        open(List.of());
        UUID id = UUID.randomUUID();
        db.blocking(c -> {
            store.loadOrCreate(c, id, 1);
            store.markDone(c, id, "crop", 1, false, null);
            store.markDone(c, id, "crop", 2, true, null);
            store.setCount(c, id, "crop", 1);
            store.setCount(c, id, "crop", 3);
            return null;
        });
        Progress p = db.blocking(c -> store.load(c, id)).orElseThrow();
        assertEquals(1, p.done.size());
        assertFalse(p.done.get("crop"));
        assertEquals(3, p.count("crop"));
        db.blocking(c -> {
            store.clearSteps(c, id);
            return null;
        });
        Progress cleared = db.blocking(c -> store.load(c, id)).orElseThrow();
        assertTrue(cleared.done.isEmpty());
        assertTrue(cleared.counts.isEmpty());
    }

    @Test
    void theFinishGuardWinsExactlyOnce() throws Exception {
        open(List.of());
        UUID id = UUID.randomUUID();
        Progress p = db.blocking(c -> store.loadOrCreate(c, id, 1));
        boolean early = db.blocking(c -> store.finish(c, id, 5, null, true));
        assertFalse(early, "a tutorial that never started can't finish");
        StepRules.start(p, FLOW, 1);
        db.blocking(c -> {
            store.save(c, p);
            return null;
        });
        List<CompletableFuture<Boolean>> tries = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tries.add(db.transaction(c -> store.finish(c, id, 10, id, true)));
        }
        long wins = tries.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();
        assertEquals(1, wins, "two finish signals in one tick pay once");
        Progress done = db.blocking(c -> store.load(c, id)).orElseThrow();
        assertEquals(State.FINISHED, done.state);
        assertNull(done.step);
        assertTrue(done.rewarded);
        assertEquals(Long.valueOf(10), done.finishedAt);
    }

    @Test
    void theStarterPackIsClaimedOncePerAccount() throws Exception {
        open(List.of());
        UUID id = UUID.randomUUID();
        db.blocking(c -> store.loadOrCreate(c, id, 1));
        List<CompletableFuture<Boolean>> tries = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tries.add(db.transaction(c -> store.claimPack(c, id)));
        }
        assertEquals(1, tries.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count());
        assertTrue(db.blocking(c -> store.load(c, id)).orElseThrow().packGiven);
    }

    @Test
    void staffResetIsSavedAndTheChestPointIsKept() throws Exception {
        open(List.of());
        UUID id = UUID.randomUUID();
        Progress p = db.blocking(c -> store.loadOrCreate(c, id, 1));
        db.blocking(c -> {
            store.setChest(c, id, "islands", 4, 101, -2);
            return null;
        });
        p = db.blocking(c -> store.load(c, id)).orElseThrow();
        StepRules.start(p, FLOW, 1);
        p.rewarded = true;
        StepRules.reset(p);
        Progress saved = p;
        db.blocking(c -> {
            store.clearSteps(c, id);
            store.save(c, saved);
            store.event(c, id, null, "staff_reset", 5, false, 0);
            return null;
        });
        Progress loaded = db.blocking(c -> store.load(c, id)).orElseThrow();
        assertEquals(State.NEW, loaded.state);
        assertTrue(loaded.rewarded);
        assertEquals("islands:4,101,-2", loaded.chest);
        int events = db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) AS n FROM tutorial_events WHERE player_uuid = ?",
                rs -> rs.getInt("n"), id).orElse(0));
        assertEquals(1, events);
    }
}

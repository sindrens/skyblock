package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.tutorial.TutorialService.State;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V003 on H2 (MySQL mode): optional steps once per account, tips once, progress saved; analytics reads and the purge. */
class OptionalStoreTest {

    private static final Logger LOG = Logger.getLogger("tutorial-optional-test");

    @TempDir
    Path dir;
    private Database db;
    private final TutorialStore store = new TutorialStore();

    @BeforeEach
    void open() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "tutorial-optional-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), LOG);
        db.start();
        new Migrations(db, LOG).migrate("core", Migrations.class.getClassLoader());
        new Migrations(db, LOG).migrate("tutorial", getClass().getClassLoader());
    }

    @AfterEach
    void close() {
        db.close();
    }

    @Test
    void anOptionalStepIsDoneAndPaidOnce() throws Exception {
        UUID id = UUID.randomUUID();
        db.blocking(c -> store.loadOrCreate(c, id, 1));
        boolean first = db.blocking(c -> store.completeOptional(c, id, "settings", 100, true));
        boolean second = db.blocking(c -> store.completeOptional(c, id, "settings", 200, true));
        assertTrue(first);
        assertFalse(second, "the guard refuses a second completion (no second reward)");
        Map<String, OptionalRules.Entry> loaded = db.blocking(c -> store.loadOptional(c, id));
        assertEquals(100, loaded.get("settings").doneAt);
        assertTrue(loaded.get("settings").rewarded);
        // The account's progress loads its optional steps too.
        Progress p = db.blocking(c -> store.load(c, id)).orElseThrow();
        assertTrue(p.optional.get("settings").done());
    }

    @Test
    void progressAndTipsAreKept() throws Exception {
        UUID id = UUID.randomUUID();
        db.blocking(c -> {
            store.setOptionalValue(c, id, "generator", 3);
            store.markTipped(c, id, "generator", 50);
            store.markTipped(c, id, "generator", 99);
            store.setOptionalValue(c, id, "generator", 4);
            return null;
        });
        OptionalRules.Entry e = db.blocking(c -> store.loadOptional(c, id)).get("generator");
        assertEquals(4, e.value);
        assertEquals(50, e.tippedAt, "the first tip time stays");
        assertFalse(e.done());
        db.blocking(c -> store.completeOptional(c, id, "generator", 70, false));
        db.blocking(c -> {
            store.setOptionalValue(c, id, "generator", 1);
            return null;
        });
        OptionalRules.Entry done = db.blocking(c -> store.loadOptional(c, id)).get("generator");
        assertEquals(4, done.value, "progress no longer changes once done");
        assertFalse(done.rewarded);
    }

    @Test
    void analyticsReadsAndPurge() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        db.blocking(c -> {
            Progress pa = store.loadOrCreate(c, a, 1);
            pa.state = State.ACTIVE;
            pa.step = "chest";
            store.save(c, pa);
            store.loadOrCreate(c, b, 1);
            store.event(c, a, "portal", "start", 10, false, 0);
            store.event(c, a, "portal", "done", 20, false, 5000);
            store.event(c, b, "island", "camera_skip", 30, true, 0);
            store.event(c, b, "settings", "opt_done", 40, true, 0);
            return null;
        });
        List<FunnelStats.Event> all = db.blocking(c -> store.events(c, 0, 1000));
        assertEquals(4, all.size());
        assertEquals("camera_skip", all.get(2).kind());
        assertTrue(all.get(2).bedrock());
        assertEquals(5000, all.get(1).millis());
        assertEquals(2, db.blocking(c -> store.events(c, 25, 1000)).size(), "only the period");
        assertEquals(1, db.blocking(c -> store.events(c, 0, 1)).size(), "row limit");
        assertEquals(Map.of(a, "chest"), db.blocking(c -> store.activeSteps(c)));
        assertEquals(2, (int) db.blocking(c -> store.purgeEvents(c, 25)));
        assertEquals(2, db.blocking(c -> store.events(c, 0, 1000)).size());
    }
}

package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Save, load, reset, rewards once per account and stats on H2 (MySQL mode, the same SQL as MariaDB). */
class TutorialStoreTest {

    @TempDir
    Path dir;
    private Database db;
    private final UUID alex = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "tutorial-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("tutorial-test"));
        db.start();
        new Migrations(db, Logger.getLogger("tutorial-test")).migrate("tutorial", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void savesAndLoadsAndSavesAgain() {
        TutorialStore.Saved s = new TutorialStore.Saved(Set.of("welcome.rowan", "welcome.menu"), Map.of("farm.harvest", 12L),
                Set.of("welcome"), 0, true);
        db.blockingTransaction(c -> {
            TutorialStore.save(c, alex, s, 1000);
            TutorialStore.save(c, alex, s, 2000);
            return null;
        });
        TutorialStore.Saved back = db.blocking(c -> TutorialStore.load(c, alex));
        assertEquals(s.done(), back.done());
        assertEquals(s.counters(), back.counters());
        assertEquals(s.chapters(), back.chapters());
        assertTrue(back.hidden());
        assertEquals(0, back.skippedAt());
    }

    @Test
    void rewardsPayOnceAndSurviveAReset() {
        assertTrue(db.blocking(c -> TutorialStore.claim(c, alex, "welcome", 1)));
        assertFalse(db.blocking(c -> TutorialStore.claim(c, alex, "welcome", 2)));
        db.blockingTransaction(c -> {
            TutorialStore.save(c, alex, new TutorialStore.Saved(Set.of("welcome.rowan"), Map.of(), Set.of("welcome"), 0, false), 1);
            TutorialStore.reset(c, alex);
            return null;
        });
        TutorialStore.Saved back = db.blocking(c -> TutorialStore.load(c, alex));
        assertTrue(back.done().isEmpty());
        assertTrue(back.chapters().isEmpty());
        assertFalse(db.blocking(c -> TutorialStore.claim(c, alex, "welcome", 3)), "a reset never pays a chapter again");
    }

    @Test
    void statsCountStartedSkippedAndFinished() {
        UUID steve = UUID.randomUUID();
        db.blockingTransaction(c -> {
            TutorialStore.save(c, alex, new TutorialStore.Saved(Set.of(), Map.of(), Set.of("welcome", "island"), 0, false), 1);
            TutorialStore.save(c, steve, new TutorialStore.Saved(Set.of(), Map.of(), Set.of("welcome"), 5, false), 1);
            return null;
        });
        TutorialStore.Stats st = db.blocking(TutorialStore::stats);
        assertEquals(2, st.started());
        assertEquals(1, st.skipped());
        assertEquals(1L, st.finished().get("welcome"), "a chapter written while skipped does not count as finished");
        assertEquals(1L, st.finished().get("island"));
    }
}

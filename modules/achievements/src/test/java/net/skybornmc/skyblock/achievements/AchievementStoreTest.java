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
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The achievements tables on H2 in MySQL mode (same SQL as MariaDB). */
class AchievementStoreTest {

    @TempDir
    Path dir;
    private Database db;
    private final AchievementStore store = new AchievementStore();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "achievements-test";
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
    void countersAddDeltasAndGaugesKeepTheHighest() {
        UUID key = UUID.randomUUID();
        db.blockingTransaction(c -> {
            store.save(c, key, List.of(new ProfileProgress.Row(Metric.ITEMS_SOLD, 100), new ProfileProgress.Row(Metric.SKILL_MINING, 20)), 1L);
            store.save(c, key, List.of(new ProfileProgress.Row(Metric.ITEMS_SOLD, 50), new ProfileProgress.Row(Metric.SKILL_MINING, 12)), 2L);
            return null;
        });
        AchievementStore.Loaded loaded = db.blocking(c -> store.load(c, key));
        assertEquals(Map.of(Metric.ITEMS_SOLD, 150L, Metric.SKILL_MINING, 20L), loaded.values());
        assertTrue(db.blocking(c -> store.load(c, UUID.randomUUID())).values().isEmpty(), "profiles are separate");
    }

    @Test
    void unlocksAreStoredOnceAndDeletedWithTheProfile() {
        UUID key = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        assertTrue(db.<Boolean>blocking(c -> store.unlock(c, key, "merchant", 1, owner, 1L)));
        assertFalse(db.<Boolean>blocking(c -> store.unlock(c, key, "merchant", 1, owner, 2L)), "the same tier twice is refused");
        assertTrue(db.<Boolean>blocking(c -> store.unlock(c, key, "merchant", 2, owner, 3L)));
        assertEquals(Map.of("merchant", 2), db.blocking(c -> store.load(c, key)).unlocks());
        db.blocking(c -> {
            store.save(c, key, List.of(new ProfileProgress.Row(Metric.ITEMS_SOLD, 5)), 1L);
            return null;
        });
        assertEquals(3, (int) db.<Integer>blocking(c -> store.delete(c, key)));
        AchievementStore.Loaded gone = db.blocking(c -> store.load(c, key));
        assertTrue(gone.values().isEmpty());
        assertTrue(gone.unlocks().isEmpty());
    }
}

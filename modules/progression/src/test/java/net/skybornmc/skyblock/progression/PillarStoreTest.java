package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.progression.Pillar;
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

/** The pillar tables on H2 in MySQL mode (same SQL as MariaDB). */
class PillarStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private final PillarStore store = new PillarStore();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "progression-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("progression-test"));
        db.start();
        new Migrations(db, Logger.getLogger("progression-test")).migrate("progression", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private void write(int season, UUID island, Pillar pillar, int week, double xp, int levels, int level) {
        PillarBuffer buffer = new PillarBuffer();
        buffer.add(island, pillar, week, xp, levels);
        PillarBuffer.Batch batch = buffer.drain(k -> level);
        db.blockingTransaction(c -> store.write(c, season, batch, 1L));
    }

    @Test
    void deltasAddUpAndLevelsNeverGoDown() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        write(0, a, Pillar.MINING, 100, 250.5, 1, 1);
        write(0, a, Pillar.MINING, 100, 100, 0, 0);
        write(0, a, Pillar.COMBAT, 100, 5, 0, 0);
        write(0, b, Pillar.MINING, 107, 1, 0, 0);
        write(1, a, Pillar.MINING, 100, 999, 3, 3);

        PillarStore.Loaded loaded = db.blocking(c -> store.load(c, 0));
        assertEquals(3, loaded.rows().size());
        PillarStore.Row mining = loaded.rows().stream()
                .filter(r -> r.island().equals(a) && r.pillar() == Pillar.MINING).findFirst().orElseThrow();
        assertEquals(350.5, mining.xp(), 1e-9);
        assertEquals(1, mining.level(), "GREATEST keeps the higher stored level");

        assertEquals(Map.of(Pillar.MINING, 1, Pillar.COMBAT, 0), db.blocking(c -> store.weeklyLevels(c, a, 0, 100)));
        assertEquals(Map.of(), db.blocking(c -> store.weeklyLevels(c, a, 0, 107)));
        assertEquals(Map.of(Pillar.MINING, 3), db.blocking(c -> store.weeklyLevels(c, a, 1, 100)));
    }

    @Test
    void milestonesAreMarkedOncePerSeason() {
        UUID a = UUID.randomUUID();
        assertTrue(db.<Boolean>blockingTransaction(c -> store.markMilestone(c, a, 0, 10, 5L)));
        assertFalse(db.<Boolean>blockingTransaction(c -> store.markMilestone(c, a, 0, 10, 6L)));
        assertTrue(db.<Boolean>blockingTransaction(c -> store.markMilestone(c, a, 0, 25, 7L)));
        assertTrue(db.<Boolean>blockingTransaction(c -> store.markMilestone(c, a, 1, 10, 8L)));
        assertEquals(Map.of(a, Set.of(10, 25)), db.blocking(c -> store.load(c, 0)).milestones());
        assertEquals(Map.of(a, Set.of(10)), db.blocking(c -> store.load(c, 1)).milestones());
    }
}

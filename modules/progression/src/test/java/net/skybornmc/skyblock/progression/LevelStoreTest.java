package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.progression.LevelSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The island level tables on H2 in MySQL mode (same SQL as MariaDB). */
class LevelStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private final LevelStore store = new LevelStore();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "level-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("level-test"));
        db.start();
        new Migrations(db, Logger.getLogger("level-test")).migrate("progression", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static LevelStore.LevelRow row(UUID island, int level, int best, long scanned) {
        Map<LevelSource, Double> points = new EnumMap<>(LevelSource.class);
        points.put(LevelSource.PILLARS, 170.0);
        points.put(LevelSource.BLOCKS, 38.7);
        return new LevelStore.LevelRow(island, 384, 295, level, best, 10, points, 6_000_000, 20_000_000, scanned, 99);
    }

    @Test
    void levelRoundTripBestNeverFallsHistoryPerDay() {
        UUID island = UUID.randomUUID();
        db.blockingTransaction(c -> {
            store.saveLevel(c, 0, row(island, 38, 38, 5), 100);
            store.saveLevel(c, 0, row(island, 36, 36, 0), 100);
            store.saveLevel(c, 0, row(island, 37, 37, 0), 101);
            return null;
        });
        List<LevelStore.LevelRow> rows = db.blocking(c -> store.loadLevels(c, 0));
        assertEquals(1, rows.size());
        LevelStore.LevelRow r = rows.getFirst();
        assertEquals(37, r.level());
        assertEquals(38, r.bestLevel(), "the season high never goes down");
        assertEquals(5, r.scannedAt(), "the last scan time never goes back");
        assertEquals(170, r.points().get(LevelSource.PILLARS), 1e-9);
        assertEquals(0, r.points().get(LevelSource.SKILLS), 1e-9);
        int days = db.blocking(c -> net.skybornmc.skyblock.common.db.Sql.queryOne(c,
                "SELECT COUNT(*) FROM island_level_history WHERE season_id = 0", rs -> rs.getInt(1)).orElse(0));
        assertEquals(2, days);
        assertEquals(0, db.blocking(c -> store.loadLevels(c, 1)).size(), "per season");
    }

    @Test
    void worthRowsUpsert() {
        UUID island = UUID.randomUUID();
        db.blockingTransaction(c -> store.saveWorth(c, 0, List.of(new LevelStore.WorthRow(island, 1, -2, 10, 5)), 1));
        db.blockingTransaction(c -> store.saveWorth(c, 0, List.of(new LevelStore.WorthRow(island, 1, -2, 12, 0)), 2));
        List<LevelStore.WorthRow> rows = db.blocking(c -> store.loadWorth(c, 0));
        assertEquals(1, rows.size());
        assertEquals(12, rows.getFirst().blocks(), 1e-9);
        assertEquals(-2, rows.getFirst().chunkZ());
    }

    @Test
    void bankSamplesLoadPruneAndLatestBefore() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        db.blockingTransaction(c -> {
            store.addBankSample(c, 0, a, 100, 5_000);
            store.addBankSample(c, 0, a, 200, 7_000);
            store.addBankSample(c, 0, a, 900, 1_000);
            store.addBankSample(c, 0, b, 50, 42);
            store.addBankSample(c, 0, b, 50, 99); // same time: the lower stays
            return null;
        });
        List<LevelStore.BankRow> before = db.blocking(c -> store.loadLatestBefore(c, 0, 500, 0));
        assertEquals(2, before.size());
        assertEquals(7_000, before.stream().filter(r -> r.island().equals(a)).findFirst().orElseThrow().balance());
        assertEquals(42, before.stream().filter(r -> r.island().equals(b)).findFirst().orElseThrow().balance());
        assertEquals(1, db.blocking(c -> store.loadBankSamples(c, 0, 500)).size());
        int pruned = db.blockingTransaction(c -> store.pruneBankSamples(c, 0, Map.of(a, 200L)));
        assertEquals(1, pruned);
        assertEquals(3, db.blocking(c -> store.loadBankSamples(c, 0, 0)).size());
    }
}

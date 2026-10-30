package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The task tables on H2 in MySQL mode (same SQL as MariaDB): deltas, deliveries, rewards paid once. */
class TaskStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private final TaskStore store = new TaskStore();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "tasks-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("tasks-test"));
        db.start();
        new Migrations(db, Logger.getLogger("tasks-test")).migrate("progression", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void bufferedDeltasAddUpPerSeason() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        TaskBuffer buffer = new TaskBuffer();
        buffer.add(a, "mine:coal_ore", 100);
        buffer.add(a, "mine:coal_ore", 20);
        buffer.add(b, "earn:any", 5000);
        Map<TaskBuffer.Key, Long> batch = buffer.drain();
        assertTrue(buffer.isEmpty());
        db.blockingTransaction(c -> store.write(c, 0, batch, 1L));
        Map<TaskBuffer.Key, Long> second = new HashMap<>();
        second.put(new TaskBuffer.Key(a, "mine:coal_ore"), 30L);
        db.blockingTransaction(c -> store.write(c, 0, second, 2L));
        db.blockingTransaction(c -> store.write(c, 1, second, 2L));
        assertEquals(150L, db.<Long>blocking(c -> store.amount(c, a, 0, "mine:coal_ore")));
        assertEquals(5000L, db.<Long>blocking(c -> store.amount(c, b, 0, "earn:any")));
        assertEquals(30L, db.<Long>blocking(c -> store.amount(c, a, 1, "mine:coal_ore")));
        TaskStore.Loaded loaded = db.blocking(c -> store.load(c, 0));
        assertEquals(2, loaded.rows().size());
    }

    @Test
    void aFailedWriteMergesBack() {
        UUID a = UUID.randomUUID();
        TaskBuffer buffer = new TaskBuffer();
        buffer.add(a, "kill:zombie", 3);
        Map<TaskBuffer.Key, Long> batch = buffer.drain();
        buffer.add(a, "kill:zombie", 2);
        buffer.mergeBack(batch);
        assertEquals(Map.of(new TaskBuffer.Key(a, "kill:zombie"), 5L), buffer.drain());
        buffer.add(a, "kill:zombie", 1);
        buffer.forget(a);
        assertTrue(buffer.isEmpty());
    }

    @Test
    void doneIsMarkedOncePerSeason() {
        UUID a = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        assertTrue(db.<Boolean>blockingTransaction(c -> store.markDone(c, a, 0, "t1_mine", member, 5L)));
        assertFalse(db.<Boolean>blockingTransaction(c -> store.markDone(c, a, 0, "t1_mine", null, 6L)));
        assertTrue(db.<Boolean>blockingTransaction(c -> store.markDone(c, a, 0, "t1_chop", null, 7L)));
        assertTrue(db.<Boolean>blockingTransaction(c -> store.markDone(c, a, 1, "t1_mine", null, 8L)));
        assertEquals(Map.of(a, Set.of("t1_mine", "t1_chop")), db.blocking(c -> store.load(c, 0)).done());
        assertEquals(Map.of(a, Set.of("t1_mine")), db.blocking(c -> store.load(c, 1)).done());
    }

    @Test
    void deliveryProgressRollsBackWithItsTransaction() {
        UUID a = UUID.randomUUID();
        db.blockingTransaction(c -> {
            store.add(c, a, 0, "deliver:t1_deliver", 40, 1L);
            return null;
        });
        // A storage take that fails after writing progress: nothing of it stays (one transaction).
        assertThrows(RuntimeException.class, () -> db.blockingTransaction(c -> {
            store.add(c, a, 0, "deliver:t1_deliver", 24, 2L);
            throw new java.sql.SQLException("storage rows changed");
        }));
        assertEquals(40L, db.<Long>blocking(c -> store.amount(c, a, 0, "deliver:t1_deliver")));
    }
}

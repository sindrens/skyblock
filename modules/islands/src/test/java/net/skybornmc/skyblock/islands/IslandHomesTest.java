package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared island homes (PHASE-5 M5.5 Homestead, owner P5-21): names, the limit (base 1 = the island home, +1 per Homestead
 * rank) and {@code island_homes} on H2 in MySQL mode (limit checked inside the transaction, moves never need a slot,
 * homes go with the island).
 */
class IslandHomesTest {

    @TempDir
    Path dir;
    private Database db;
    private UUID owner;
    private UUID island;
    private final Island.Home spot = new Island.Home(1.5, 70, 2.5, 90f, 0f);

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "homes-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("test-db"));
        db.start();
        new Migrations(db, Logger.getLogger("test")).migrate("islands", getClass().getClassLoader());
        owner = UUID.randomUUID();
        IslandStore.CreateResult result = db.blocking(c -> IslandStore.createWithRetry(c, owner, 1, 3, "islands", 1000L));
        island = assertInstanceOf(IslandStore.CreateResult.Created.class, result).island().id();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private IslandHomeStore.Saved save(String name, int allowed) {
        return db.blockingTransaction(c -> IslandHomeStore.save(c, island, name, spot, owner, allowed, 5L));
    }

    @Test
    void namesAreShortLowercaseWords() {
        assertEquals("farm", IslandHomeRules.normalize(" Farm "));
        assertEquals("nether_hub2", IslandHomeRules.normalize("nether_hub2"));
        assertNull(IslandHomeRules.normalize(""));
        assertNull(IslandHomeRules.normalize("two words"));
        assertNull(IslandHomeRules.normalize("a-b"));
        assertNull(IslandHomeRules.normalize("x".repeat(17)));
        assertNull(IslandHomeRules.normalize(null));
    }

    @Test
    void limitIsOnePerHomesteadRank() {
        // Without Homestead only the island home exists: no named home.
        assertEquals(IslandHomeRules.SetResult.LIMIT, IslandHomeRules.set(Set.of(), "farm", 0));
        // Homestead I: one named home (2 homes in total with the island home).
        assertEquals(IslandHomeRules.SetResult.NEW, IslandHomeRules.set(Set.of(), "farm", 1));
        assertEquals(IslandHomeRules.SetResult.LIMIT, IslandHomeRules.set(Set.of("farm"), "mine", 1));
        // Moving an existing home never needs a free slot, even above the limit (ranks taken away).
        assertEquals(IslandHomeRules.SetResult.MOVE, IslandHomeRules.set(Set.of("farm", "mine"), "farm", 1));
        assertEquals(IslandHomeRules.SetResult.NEW, IslandHomeRules.set(Set.of("farm"), "mine", 2));
        assertEquals(IslandHomeRules.SetResult.BAD_NAME, IslandHomeRules.set(Set.of(), null, 2));
        assertEquals(2, IslandHomeRules.total(1));
        assertEquals(3, IslandHomeRules.total(2));
    }

    @Test
    void storeKeepsTheLimitAndMoves() {
        assertEquals(IslandHomeStore.Saved.LIMIT, save("farm", 0));
        assertEquals(IslandHomeStore.Saved.NEW, save("farm", 1));
        assertEquals(IslandHomeStore.Saved.LIMIT, save("mine", 1));
        assertEquals(IslandHomeStore.Saved.MOVED, save("farm", 1));
        assertEquals(IslandHomeStore.Saved.NEW, save("mine", 2));
        Map<String, Island.Home> loaded = db.blocking(IslandHomeStore::loadAll).get(island);
        assertEquals(Set.of("farm", "mine"), loaded.keySet());
        assertEquals(spot, loaded.get("farm"));
        assertTrue(db.<Boolean>blocking(c -> IslandHomeStore.delete(c, island, "farm")));
        assertFalse(db.<Boolean>blocking(c -> IslandHomeStore.delete(c, island, "farm")));
        assertEquals(Set.of("mine"), db.blocking(IslandHomeStore::loadAll).get(island).keySet());
    }

    @Test
    void twoMembersCantBothTakeTheLastSlot() throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger made = new AtomicInteger();
        List<CompletableFuture<Void>> runs = new ArrayList<>();
        for (String name : List.of("a", "b")) {
            runs.add(CompletableFuture.runAsync(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                try {
                    if (save(name, 1) == IslandHomeStore.Saved.NEW) {
                        made.incrementAndGet();
                    }
                } catch (RuntimeException ignored) {
                    // a lock conflict rolls back: no home
                }
            }));
        }
        go.countDown();
        CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).join();
        assertTrue(made.get() <= 1);
        assertTrue(db.blocking(IslandHomeStore::loadAll).getOrDefault(island, Map.of()).size() <= 1);
    }

    @Test
    void homesGoWithTheIsland() {
        save("farm", 1);
        db.blockingTransaction(c -> IslandStore.disband(c, island, owner, IslandStore.REASON_DISBAND, owner, 10L));
        assertNull(db.blocking(IslandHomeStore::loadAll).get(island));
    }
}

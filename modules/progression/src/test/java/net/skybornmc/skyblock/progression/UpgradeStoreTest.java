package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code island_upgrades} on H2 in MySQL mode (same SQL as MariaDB), with a stand-in for the economy's guarded payment
 * ({@code UPDATE … SET balance = balance - ? WHERE balance >= ?} in the same transaction as the level row): a level exists
 * exactly when it was paid, never twice, and the effects come back after a restart.
 */
class UpgradeStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private final UpgradeStore store = new UpgradeStore();
    private final UUID island = UUID.randomUUID();
    private final UUID buyer = UUID.randomUUID();

    private Database open() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "upgrades-test";
        config.poolSize = 4;
        Database d = new Database(config, dir.toFile(), Logger.getLogger("upgrades-test"));
        d.start();
        new Migrations(d, Logger.getLogger("upgrades-test")).migrate("progression", getClass().getClassLoader());
        d.blocking(c -> Sql.update(c, "CREATE TABLE IF NOT EXISTS test_bank (owner_id BINARY(16) NOT NULL PRIMARY KEY, balance BIGINT NOT NULL)"));
        return d;
    }

    @BeforeEach
    void start() throws Exception {
        db = open();
        db.blocking(c -> Sql.update(c, "INSERT INTO test_bank (owner_id, balance) VALUES (?, ?)", Sql.toBytes(island), 30_000L));
    }

    @AfterEach
    void stop() {
        db.close();
    }

    /** The guarded payment: the guard (level row) first, then the money; any failure rolls both back. */
    private boolean pay(String upgrade, int level, long cost) {
        try {
            return db.blockingTransaction(c -> {
                if (!store.buy(c, island, 0, upgrade, level, buyer, 1L)) {
                    return false; // DUPLICATE: nothing paid
                }
                if (!withdraw(c, cost)) {
                    throw new SQLException("insufficient funds"); // INSUFFICIENT_FUNDS: level row rolled back too
                }
                return true;
            });
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean withdraw(Connection c, long cost) throws SQLException {
        return Sql.update(c, "UPDATE test_bank SET balance = balance - ? WHERE owner_id = ? AND balance >= ?", cost, Sql.toBytes(island), cost) == 1;
    }

    private long balance() {
        return db.<Long>blocking(c -> Sql.queryOne(c, "SELECT balance FROM test_bank WHERE owner_id = ?", rs -> rs.getLong(1),
                Sql.toBytes(island)).orElse(-1L));
    }

    private int level(String upgrade) {
        return db.<Integer>blocking(c -> store.level(c, island, 0, upgrade));
    }

    @Test
    void levelsGoUpOneAtATimeAndArePaidOnce() {
        assertTrue(pay("island_size", 1, 3_350));
        assertEquals(1, level("island_size"));
        assertEquals(26_650, balance());
        // A second click on the same level (old menu, retry): refused, nothing paid twice.
        assertFalse(pay("island_size", 1, 3_350));
        assertEquals(26_650, balance());
        // Skipping a level is refused.
        assertFalse(pay("island_size", 3, 1));
        assertEquals(1, level("island_size"));
        assertTrue(pay("island_size", 2, 22_900));
        assertEquals(2, level("island_size"));
        assertEquals(3_750, balance());
        // Level 0 does not exist.
        assertFalse(db.<Boolean>blockingTransaction(c -> store.buy(c, island, 0, "island_size", 0, buyer, 1L)));
    }

    @Test
    void notEnoughMoneyLeavesNoLevel() {
        assertFalse(pay("island_size", 1, 30_001));
        assertEquals(0, level("island_size"), "the level row rolled back with the failed payment");
        assertEquals(30_000, balance());
        assertTrue(pay("island_size", 1, 30_000));
        assertEquals(0, balance());
    }

    @Test
    void seasonsAndIslandsAreSeparate() {
        assertTrue(pay("member_slots", 1, 100));
        UUID other = UUID.randomUUID();
        assertTrue(db.<Boolean>blockingTransaction(c -> store.buy(c, island, 1, "member_slots", 1, buyer, 1L)));
        assertTrue(db.<Boolean>blockingTransaction(c -> store.buy(c, other, 0, "member_slots", 1, null, 1L)));
        Map<UUID, Map<String, Integer>> season0 = db.blocking(c -> store.load(c, 0));
        assertEquals(2, season0.size());
        assertEquals(Map.of("member_slots", 1), season0.get(island));
        assertEquals(Map.of("member_slots", 1), db.blocking(c -> store.load(c, 1)).get(island));
    }

    @Test
    void twoMembersBuyingAtOnceOnlyPayOnce() throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger bought = new AtomicInteger();
        List<CompletableFuture<Void>> runs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            runs.add(CompletableFuture.runAsync(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (pay("island_size", 1, 3_350)) {
                    bought.incrementAndGet();
                }
            }));
        }
        go.countDown();
        CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).join();
        assertEquals(1, bought.get());
        assertEquals(1, level("island_size"));
        assertEquals(26_650, balance());
    }

    @Test
    void effectsComeBackAfterARestart() throws Exception {
        assertTrue(pay("island_size", 1, 1_000));
        assertTrue(pay("island_size", 2, 1_000));
        assertTrue(pay("spawner_stack_limit", 1, 1_000));
        assertTrue(pay("bank_capacity", 1, 1_000));
        db.close();
        db = open(); // server restart: same database file
        Map<String, Integer> loaded = db.blocking(c -> store.load(c, 0)).get(island);
        assertNotNull(loaded);
        UpgradeCatalog catalog = bundled();
        assertEquals(7, UpgradeRules.raiseTo(catalog, loaded, UpgradeEffect.ISLAND_SIZE, 3), "border back to 7x7 if it was behind");
        assertEquals(-1, UpgradeRules.raiseTo(catalog, loaded, UpgradeEffect.ISLAND_SIZE, 7), "already right: nothing to do");
        assertEquals(OptionalInt.of(16), UpgradeRules.spawnerStack(catalog, loaded));
        assertEquals(1, UpgradeRules.whole(catalog, loaded, UpgradeEffect.BANK_CAPACITY));
        assertEquals(0, UpgradeRules.whole(catalog, loaded, UpgradeEffect.BANK_INTEREST));
        assertEquals(2.0, UpgradeRules.points(catalog, loaded, new UpgradeRules.Points(0.5, 1, 1.5, 4, 7)), 1e-9,
                "size I + II (tiers 1, 3) and stack I + capacity I (tier 1): 4 x 0.5");
    }

    static UpgradeCatalog bundled() throws Exception {
        try (var in = UpgradeStoreTest.class.getClassLoader().getResourceAsStream(UpgradeCatalog.FILE)) {
            assertNotNull(in);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            Set<String> problems = new HashSet<>();
            UpgradeCatalog catalog = UpgradeCatalog.load(yaml, id -> true, problems::add);
            assertEquals(Set.of(), problems);
            return catalog;
        }
    }
}

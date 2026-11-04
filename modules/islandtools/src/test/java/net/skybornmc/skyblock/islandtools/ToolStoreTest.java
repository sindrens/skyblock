package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Island tools against a real (H2) database: island-owned progress, no double purchase or prestige, the storage guard. */
class ToolStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private ToolStore store;
    private final UUID profile = UUID.randomUUID();
    private final UUID island = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "islandtools-test";
        dbc.poolSize = 4;
        dbc.slowQueryMs = 60_000;
        Logger log = Logger.getLogger("islandtools-test");
        db = new Database(dbc, dir.toFile(), log);
        db.start();
        new Migrations(db, log).migrate("core", getClass().getClassLoader());
        new Migrations(db, log).migrate("islandtools", getClass().getClassLoader());
        store = new ToolStore(db);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void oldIslandLevelsSeedTheNewRowOnce() {
        db.run(c -> Sql.update(c, "INSERT INTO island_tool_levels (island_id, kind, tier, upgrades, updated_at) VALUES (?, ?, 2, ?, 0)",
                Sql.toBytes(island), "hoe", "shard_finder=3,row=4")).join();
        Upgrades perks = Upgrades.parse(new IslandToolsConfig().perks, w -> { });
        Map<Kind, ToolStore.Prog> first = store.loadIsland(island, (k, old) -> Upgrades.encode(ToolRules.migrate(k, old, perks))).join();
        assertEquals("shard_finder=300", first.get(Kind.HOE).upgrades(), "carried over, row dropped, tier gone");
        assertEquals(ToolStore.Prog.EMPTY, first.get(Kind.PICKAXE));
        Map<Kind, ToolStore.Prog> again = store.loadIsland(island, (k, old) -> "broken=1").join();
        assertEquals("shard_finder=300", again.get(Kind.HOE).upgrades(), "seeded only once");
    }

    @Test
    void purchasesAndPrestigeNeverTwice() {
        store.loadIsland(island, (k, old) -> "").join();
        assertTrue(db.<Boolean>transaction(c -> ToolStore.buy(c, island, Kind.HOE, 0, "fortune=1")).join());
        assertFalse(db.<Boolean>transaction(c -> ToolStore.buy(c, island, Kind.HOE, 0, "fortune=2")).join(), "the second click sees the old version");
        store.addXp(Map.of(island, Map.of(Kind.HOE, 40L))).join();
        ToolStore.Prog now = store.loadIsland(island, (k, old) -> "").join().get(Kind.HOE);
        assertEquals(new ToolStore.Prog(40, 0, "fortune=1", 1), now, "XP adds up without touching the version");
        assertFalse(db.<Boolean>transaction(c -> ToolStore.prestige(c, island, Kind.HOE, now, 0, 48)).join(), "not at max yet");
        assertTrue(db.<Boolean>transaction(c -> ToolStore.prestige(c, island, Kind.HOE, now, 8, 48)).join(), "pending XP counts");
        assertFalse(db.<Boolean>transaction(c -> ToolStore.prestige(c, island, Kind.HOE, now, 8, 48)).join(), "never twice");
        assertEquals(new ToolStore.Prog(0, 1, "", 2), store.loadIsland(island, (k, old) -> "").join().get(Kind.HOE));
    }

    @Test
    void handlesAreGivenOncePerIsland() {
        assertEquals(Set.of(), store.given(profile, island).join());
        store.markGiven(profile, island, EnumSet.of(Kind.HOE)).join();
        assertEquals(Set.of(Kind.HOE), store.given(profile, island).join());
        assertEquals(Set.of(), store.given(profile, UUID.randomUUID()).join(), "a new island gives new handles");
    }

    @Test
    void storageAddsAndTakesSafely() {
        store.add(profile, Map.of("wheat", 100L)).join();
        store.add(profile, Map.of("wheat", 28L)).join();
        assertEquals(128L, (long) store.storage(profile).join().get("wheat"));
        assertTrue(db.<Boolean>transaction(c -> ToolStore.take(c, profile, "wheat", 64)).join());
        assertFalse(db.<Boolean>transaction(c -> ToolStore.take(c, profile, "wheat", 65)).join(), "never below zero");
        assertEquals(64L, (long) store.storage(profile).join().get("wheat"));
    }
}

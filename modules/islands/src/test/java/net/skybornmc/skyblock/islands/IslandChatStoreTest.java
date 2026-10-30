package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Island chat role colors against a real H2 database (MySQL mode): leader-only saves, reset to default, island delete. */
class IslandChatStoreTest {

    @TempDir
    Path dir;
    private Database db;
    private UUID owner;
    private UUID island;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "chat-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("test-db"));
        db.start();
        new Migrations(db, Logger.getLogger("test")).migrate("islands", getClass().getClassLoader());
        owner = UUID.randomUUID();
        IslandStore.CreateResult result = db.blocking(c -> IslandStore.createWithRetry(c, owner, 1, 3, 0, "islands", 1000L));
        island = assertInstanceOf(IslandStore.CreateResult.Created.class, result).island().id();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private IslandChatStore.SaveResult save(UUID who, Role role, String color) {
        return db.blockingTransaction(c -> IslandChatStore.save(c, island, who, role, color, 2000L));
    }

    @Test
    void leaderSavesAndResetsColors() {
        assertEquals(IslandChatStore.SaveResult.SAVED, save(owner, Role.MOD, "yellow"));
        assertEquals(IslandChatStore.SaveResult.SAVED, save(owner, Role.MOD, "blue")); // replaces, never two rows
        assertEquals(IslandChatStore.SaveResult.SAVED, save(owner, Role.LEADER, "red"));
        Map<UUID, Map<Role, String>> all = db.blocking(IslandChatStore::loadAll);
        assertEquals(Map.of(Role.MOD, "blue", Role.LEADER, "red"), all.get(island));
        assertEquals(IslandChatStore.SaveResult.SAVED, save(owner, Role.MOD, null)); // back to the default
        assertNull(db.blocking(IslandChatStore::loadAll).get(island).get(Role.MOD));
    }

    @Test
    void nonLeadersCantSave() {
        assertEquals(IslandChatStore.SaveResult.NOT_LEADER, save(UUID.randomUUID(), Role.MEMBER, "aqua"));
        assertTrue(db.blocking(IslandChatStore::loadAll).isEmpty());
    }

    @Test
    void colorsGoWithTheIsland() {
        save(owner, Role.MEMBER, "aqua");
        db.blockingTransaction(c -> {
            IslandChatStore.deleteIsland(c, island);
            return null;
        });
        assertTrue(db.blocking(IslandChatStore::loadAll).isEmpty());
    }

    @Test
    void unknownStoredRolesAreSkipped() {
        assertEquals(Role.ADMIN, IslandChatStore.role("ADMIN"));
        assertNull(IslandChatStore.role("coop"));
        assertNull(IslandChatStore.role("captain"));
        assertNull(IslandChatStore.role(null));
    }
}

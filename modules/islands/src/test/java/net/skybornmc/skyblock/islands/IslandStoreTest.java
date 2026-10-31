package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Creation, slot allocation and loading against a real H2 database (MySQL mode, same SQL as MariaDB). */
class IslandStoreTest {

    private static final long DAY = 86_400_000L;

    @TempDir
    Path dir;
    private Database db;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "islands-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("test-db"));
        db.start();
        new Migrations(db, Logger.getLogger("test")).migrate("islands", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private IslandStore.CreateResult create(UUID owner, long now) {
        return db.blocking(c -> IslandStore.createWithRetry(c, owner, 1, 3, "islands", now));
    }

    @Test
    void createsIslandsInSpiralSlots() {
        for (int i = 0; i < 10; i++) {
            IslandStore.CreateResult result = create(UUID.randomUUID(), 1000L + i);
            Island island = assertInstanceOf(IslandStore.CreateResult.Created.class, result).island();
            assertEquals(i, island.slotId());
            assertArrayEquals(IslandGrid.spiral(i), new int[]{island.gridX(), island.gridZ()});
            assertEquals(3, island.sizeChunks());
            assertTrue(island.home().isEmpty());
        }
    }

    @Test
    void onePlayerOneIsland() {
        UUID owner = UUID.randomUUID();
        assertInstanceOf(IslandStore.CreateResult.Created.class, create(owner, 1000L));
        assertInstanceOf(IslandStore.CreateResult.AlreadyHasIsland.class, create(owner, 2000L));
        int islands = db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM islands", rs -> rs.getInt(1)).orElse(0));
        int slots = db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM island_slots", rs -> rs.getInt(1)).orElse(0));
        assertEquals(1, islands);
        assertEquals(1, slots, "the failed attempt must not keep a slot");
    }

    @Test
    void parallelCreationsNeverShareASlotOrGiveTwoIslands() throws Exception {
        UUID twice = UUID.randomUUID();
        List<CompletableFuture<IslandStore.CreateResult>> futures = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            UUID owner = i < 2 ? twice : UUID.randomUUID();
            futures.add(db.async(c -> IslandStore.createWithRetry(c, owner, 1, 3, "islands", 1000L)));
        }
        Set<Integer> slots = new HashSet<>();
        int created = 0;
        for (CompletableFuture<IslandStore.CreateResult> f : futures) {
            if (f.get() instanceof IslandStore.CreateResult.Created c) {
                assertTrue(slots.add(c.island().slotId()), "slot handed out twice");
                created++;
            }
        }
        assertEquals(11, created, "the doubled player gets exactly one island");
    }

    @Test
    void noCooldownAfterDisbandAndFreeSlotReuse() {
        UUID owner = UUID.randomUUID();
        Island first = ((IslandStore.CreateResult.Created) create(owner, 1000L)).island();
        create(UUID.randomUUID(), 1001L);
        // what M2.7 disband will do: mark deleted, drop members, free the slot
        long disbandAt = 10 * DAY;
        db.blocking(c -> {
            Sql.update(c, "UPDATE islands SET deleted_at = ? WHERE id = ?", disbandAt, first.id());
            Sql.update(c, "DELETE FROM island_members WHERE island_id = ?", first.id());
            return Sql.update(c, "UPDATE island_slots SET state = ?, island_id = NULL WHERE id = ?", IslandStore.STATE_FREE, first.slotId());
        });
        // Owner answers 121/259: no creation cooldown, a new island right after the disband.
        Island again = assertInstanceOf(IslandStore.CreateResult.Created.class, create(owner, disbandAt + 1)).island();
        assertEquals(first.slotId(), again.slotId(), "the freed slot is reused first");
        assertEquals(disbandAt, db.blocking(c -> IslandStore.lastDisband(c, owner)).orElseThrow());
    }

    @Test
    void loadsIslandsWithMembersHomeAndSize() {
        UUID owner = UUID.randomUUID();
        Island island = ((IslandStore.CreateResult.Created) create(owner, 5000L)).island();
        db.blocking(c -> {
            IslandStore.saveHome(c, island.id(), new Island.Home(264.5, 101, 264.5, 180f, 0f));
            return Sql.update(c, "UPDATE islands SET size_chunks = 5 WHERE id = ?", island.id());
        });
        List<Island> loaded = db.blocking(c -> IslandStore.loadAll(c, "islands"));
        assertEquals(1, loaded.size());
        Island back = loaded.getFirst();
        assertEquals(island.id(), back.id());
        assertEquals(owner, back.owner());
        assertEquals(5, back.sizeChunks());
        assertEquals(Role.LEADER, back.role(owner).orElseThrow());
        assertEquals(new Island.Home(264.5, 101, 264.5, 180f, 0f), back.home().orElseThrow());
        assertEquals(1, back.seasonId());
        assertTrue(!back.team());
        assertEquals(1, db.blocking(IslandStore::slots).size());
    }

    @Test
    void rolePermissionOverridesRoundTrip() {
        Island island = ((IslandStore.CreateResult.Created) create(UUID.randomUUID(), 1000L)).island();
        db.blocking(c -> {
            IslandStore.savePerm(c, island.id(), PermRole.VISITOR, IslandPerm.INTERACT, true, 1L);
            IslandStore.savePerm(c, island.id(), PermRole.MEMBER, IslandPerm.CONTAINERS, true, 2L);
            IslandStore.savePerm(c, island.id(), PermRole.MEMBER, IslandPerm.CONTAINERS, false, 3L); // overwrite, no duplicate
            IslandStore.savePerm(c, island.id(), PermRole.COOP, IslandPerm.FIRE, true, 4L);
            IslandStore.savePerm(c, island.id(), PermRole.COOP, IslandPerm.FIRE, null, 5L);          // back to default
            // a row written by a newer version with an unknown permission is ignored on load
            return Sql.update(c, "INSERT INTO island_role_perms (island_id, role, perm, allowed, updated_at) VALUES (?, ?, ?, ?, ?)",
                    island.id(), "member", "teleport-cats", true, 6L);
        });
        int rows = db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM island_role_perms", rs -> rs.getInt(1)).orElse(0));
        assertEquals(3, rows);
        Island back = db.blocking(c -> IslandStore.loadAll(c, "islands")).getFirst();
        assertEquals(Boolean.TRUE, back.perms().override(PermRole.VISITOR, IslandPerm.INTERACT));
        assertEquals(Boolean.FALSE, back.perms().override(PermRole.MEMBER, IslandPerm.CONTAINERS));
        assertNull(back.perms().override(PermRole.COOP, IslandPerm.FIRE));
        RolePermissions defaults = new IslandsConfig().rolePermissions().permissions();
        assertTrue(defaults.allows(back.perms(), PermRole.VISITOR, IslandPerm.INTERACT));
        assertFalse(defaults.allows(back.perms(), PermRole.VISITOR, IslandPerm.BREAK));
        assertFalse(defaults.allows(back.perms(), PermRole.MEMBER, IslandPerm.CONTAINERS));
    }

    @Test
    void presetSavesEveryCellInOneTransaction() {
        Island island = ((IslandStore.CreateResult.Created) create(UUID.randomUUID(), 1000L)).island();
        db.blocking(c -> {
            IslandStore.savePerm(c, island.id(), PermRole.COOP, IslandPerm.FIRE, true, 1L);
            return null;
        });
        new IslandStore(db).savePerms(island.id(), java.util.List.of(
                new PermPresets.Change(PermRole.VISITOR, IslandPerm.INTERACT, true),
                new PermPresets.Change(PermRole.MEMBER, IslandPerm.CONTAINERS, false),
                new PermPresets.Change(PermRole.COOP, IslandPerm.FIRE, null))).join();
        Island back = db.blocking(c -> IslandStore.loadAll(c, "islands")).getFirst();
        assertEquals(Boolean.TRUE, back.perms().override(PermRole.VISITOR, IslandPerm.INTERACT));
        assertEquals(Boolean.FALSE, back.perms().override(PermRole.MEMBER, IslandPerm.CONTAINERS));
        assertNull(back.perms().override(PermRole.COOP, IslandPerm.FIRE));
        int rows = db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM island_role_perms", rs -> rs.getInt(1)).orElse(0));
        assertEquals(2, rows);
    }

    @Test
    void unknownRolesFallBackToMember() {
        assertEquals(Role.MOD, IslandStore.role("mod"));
        assertEquals(Role.MEMBER, IslandStore.role("weird"));
        assertEquals(Role.MEMBER, IslandStore.role(null));
    }
}

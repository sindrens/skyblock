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
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase P7 (spec 13.2 island-ban): a staff island ban keeps even members off that one island (only the staff bypass
 * ignores it), is saved with its reference and lifted by it exactly once, and run-out bans are not loaded.
 */
class StaffIslandBanTest {

    @TempDir
    Path dir;
    private Database db;
    private VisitStore store;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "staff-ban-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("test-db"));
        db.start();
        new Migrations(db, Logger.getLogger("test")).migrate("islands", getClass().getClassLoader());
        store = new VisitStore(db);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void aStaffBanKeepsEvenMembersOff() {
        for (Role role : Role.values()) {
            assertEquals(VisitRules.Access.BANNED, VisitRules.access(role, false, false, false, false, true), role.name());
        }
        assertEquals(VisitRules.Access.BANNED, VisitRules.access(null, false, false, false, false, true));
    }

    @Test
    void theStaffBypassStillEnters() {
        assertEquals(VisitRules.Access.MEMBER, VisitRules.access(Role.MEMBER, false, false, true, false, true));
        assertEquals(VisitRules.Access.BYPASS, VisitRules.access(null, false, false, true, false, true));
    }

    @Test
    void withoutAStaffBanNothingChanges() {
        assertEquals(VisitRules.Access.MEMBER, VisitRules.access(Role.MEMBER, true, true, false, false, false));
        assertEquals(VisitRules.Access.VISITOR, VisitRules.access(null, false, false, false, false, false));
    }

    @Test
    void savedLoadedAndLiftedByReference() {
        UUID island = UUID.randomUUID();
        UUID steve = UUID.randomUUID();
        long now = System.currentTimeMillis();
        store.staffBan(island, steve, now + 60_000, "griefing", "Alex", "punishment:7").join();
        store.staffBan(island, UUID.randomUUID(), now - 1, "old", "Alex", "punishment:1").join(); // already ran out
        store.staffBan(island, UUID.randomUUID(), Long.MAX_VALUE, "perm", "Alex", "punishment:8").join();
        List<VisitStore.StaffBan> loaded = store.loadStaffBansBlocking(now);
        assertEquals(2, loaded.size());
        assertTrue(loaded.stream().anyMatch(b -> b.uuid().equals(steve) && "punishment:7".equals(b.reference())));
        assertEquals(1, store.liftStaffBans("punishment:7").join().size());
        assertEquals(0, store.liftStaffBans("punishment:7").join().size(), "lifted once");
        assertEquals(1, store.loadStaffBansBlocking(now).size());
    }
}

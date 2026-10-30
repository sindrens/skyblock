package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /crates admin} (P10-41): staff take rules, the SQL on H2, amount parsing, confirm threshold, profile picker. */
class CratesAdminTest {

    @TempDir
    Path dir;
    private Database db;
    private final UUID profile = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "crates-admin-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("crates-admin-test"));
        db.start();
        new Migrations(db, Logger.getLogger("crates-admin-test")).migrate("crates", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void staffTakeUsesBoundFirstAndNeverPartOfIt() {
        assertEquals(new CrateMath.KeyUse(3, 0), CrateMath.staffTake(5, 2, 3));
        assertEquals(new CrateMath.KeyUse(5, 1), CrateMath.staffTake(5, 2, 6), "bound first, then tradeable");
        assertEquals(new CrateMath.KeyUse(0, 2), CrateMath.staffTake(0, 2, 2));
        assertEquals(new CrateMath.KeyUse(5, 2), CrateMath.staffTake(5, 2, 7), "everything");
        assertNull(CrateMath.staffTake(5, 2, 8), "too few: nothing, not a part");
        assertNull(CrateMath.staffTake(5, 2, 0));
        assertNull(CrateMath.staffTake(5, 2, -1));
        assertNull(CrateMath.staffTake(-4, -1, 1), "broken negative counters never allow a take");
    }

    @Test
    void staffTakeOnTheDatabaseIsAllOrNothing() {
        db.blockingTransaction(c -> {
            CrateStore.addKeys(c, profile, "ember", 4, 3, 1);
            return null;
        });
        CrateStore.StaffTake first = db.blockingTransaction(c -> CrateStore.staffTake(c, profile, "ember", 6, 2, 3));
        assertTrue(first.done());
        assertEquals(4, first.bound());
        assertEquals(2, first.tradeable());
        assertEquals(new CrateStore.Keys(4, 3), first.before());
        assertEquals(new CrateStore.Keys(0, 1), db.blocking(c -> CrateStore.keys(c, profile, "ember")));

        CrateStore.StaffTake tooMany = db.blockingTransaction(c -> CrateStore.staffTake(c, profile, "ember", 2, 3, 3));
        assertFalse(tooMany.done());
        assertEquals(1, tooMany.before().total(), "the answer says how many there are");
        assertEquals(new CrateStore.Keys(0, 1), db.blocking(c -> CrateStore.keys(c, profile, "ember")), "nothing changed");

        CrateStore.StaffTake none = db.blockingTransaction(c -> CrateStore.staffTake(c, UUID.randomUUID(), "ember", 1, 4, 3));
        assertFalse(none.done(), "a profile without a row has 0 keys");
        assertEquals(0, none.before().total());
    }

    @Test
    void amountsAreWholeNumbersInRange() {
        assertEquals(Optional.of(1), CratesAdmin.parseAmount("1", 1000));
        assertEquals(Optional.of(1000), CratesAdmin.parseAmount("1000", 1000));
        assertEquals(Optional.empty(), CratesAdmin.parseAmount("1001", 1000));
        assertEquals(Optional.empty(), CratesAdmin.parseAmount("0", 1000));
        assertEquals(Optional.empty(), CratesAdmin.parseAmount("-5", 1000));
        assertEquals(Optional.empty(), CratesAdmin.parseAmount("1.5", 1000));
        assertEquals(Optional.empty(), CratesAdmin.parseAmount("1k", 1000));
        assertEquals(Optional.empty(), CratesAdmin.parseAmount("99999999999", 1000));
    }

    @Test
    void confirmOnlyAboveTheThreshold() {
        CratesSettings s = new CratesSettings();
        assertFalse(CratesAdmin.needsConfirm(s.adminConfirmAbove, s.adminConfirmAbove), "exactly the threshold runs at once");
        assertTrue(CratesAdmin.needsConfirm(s.adminConfirmAbove + 1, s.adminConfirmAbove));
        assertTrue(CratesAdmin.needsConfirm(1, 0), "0 = always confirm");
        assertTrue(CratesAdmin.needsConfirm(1, -3), "a broken negative value confirms everything");
        assertTrue(s.adminMaxAmount <= CrateKeysService.MAX_PER_CALL);
        assertFalse(s.adminDefaultTradeable, "a give without a type adds bound keys (safest)");
    }

    @Test
    void pickerStartsOnTheActiveProfileAndCycles() {
        UUID player = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        CratesAdmin.Target t = CratesAdmin.build(player, "Steve", List.of(player, second, third), List.of("Profile 1", "Farm", "Profile 3"),
                second, "Profile 1");
        assertEquals(second, t.key());
        assertEquals("Farm", t.profileName());
        assertEquals(third, t.next().key());
        assertEquals(player, t.next().next().key(), "wraps around");
        assertEquals(player, CratesAdmin.build(player, "Steve", List.of(player, second), List.of("a", "b"), UUID.randomUUID(), "x").key(),
                "an unknown active key falls back to the first profile");
        CratesAdmin.Target alone = CratesAdmin.build(player, "Steve", List.of(), List.of(), null, "Profile 1");
        assertEquals(player, alone.key(), "no profiles loaded: the player UUID is profile 1");
        assertEquals("Profile 1", alone.profileName());
        assertEquals(alone.key(), alone.next().key());
    }

    @Test
    void adminMessagesExistAndPermissionIsDeclared() throws Exception {
        YamlConfiguration yml = Fixtures.resource("messages/crates.yml");
        for (CratesAdmin.KeyKind kind : CratesAdmin.KeyKind.values()) {
            assertTrue(yml.contains("admin.kind." + kind.name().toLowerCase(java.util.Locale.ROOT)), kind.name());
        }
        String perms = Files.readString(Path.of("src/main/resources/permissions/crates.yml"));
        assertTrue(perms.contains(CratesAdmin.PERM_ADMIN + ":"));
        String grants = Files.readString(Path.of("../../server/permissions/crates.txt"));
        assertTrue(grants.contains("lp group admin permission set " + CratesAdmin.PERM_ADMIN + " true"));
    }
}

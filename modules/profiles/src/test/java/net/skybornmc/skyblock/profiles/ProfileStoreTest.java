package net.skybornmc.skyblock.profiles;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.profile.Profile;
import net.skybornmc.skyblock.common.profile.SlotRules;
import net.skybornmc.skyblock.common.profile.SlotSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The profiles SQL: migration of existing players to profile 1, create/switch/delete, state + settings separation, slots. */
class ProfileStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private final ProfileStore store = new ProfileStore();
    private final UUID oldPlayer = UUID.randomUUID();
    private final UUID newPlayer = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "profiles-test";
        dbc.poolSize = 2;
        dbc.slowQueryMs = 60_000;
        db = new Database(dbc, dir.toFile(), Logger.getLogger("profiles-test"));
        db.start();
        new Migrations(db, Logger.getLogger("profiles-test")).migrate("core", getClass().getClassLoader());
        // A player from before profiles, with playtime and settings.
        db.blocking(c -> Sql.update(c, "INSERT INTO players (uuid, name, name_lower, first_seen, last_seen, playtime_ms, bedrock, settings) "
                + "VALUES (?, 'Steve', 'steve', 100, 200, 5000, FALSE, ?)", oldPlayer, "{\"setting.pm\":\"false\",\"vanished\":\"true\"}"));
        new Migrations(db, Logger.getLogger("profiles-test")).migrate("profiles", getClass().getClassLoader());
        db.blocking(c -> Sql.update(c, "INSERT INTO players (uuid, name, name_lower, first_seen, last_seen, playtime_ms, bedrock) "
                + "VALUES (?, 'Alex', 'alex', 300, 300, 0, FALSE)", newPlayer));
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private <T> T tx(net.skybornmc.skyblock.common.db.Sql.SqlFunction<T> work) {
        return db.blockingTransaction(work);
    }

    @Test
    void existingPlayersBecomeProfileOneWithTheirOwnUuid() {
        ProfileStore.Account account = tx(c -> store.loadOrCreate(c, oldPlayer, 1_000));
        assertEquals(oldPlayer, account.active());
        assertEquals(1, account.profiles().size());
        Profile p = account.profiles().getFirst();
        assertEquals(oldPlayer, p.id());
        assertEquals(oldPlayer, p.owner());
        assertEquals(1, p.slot());
        assertEquals(5000, p.playtimeMillis());
        assertEquals(100, p.createdAt());
        assertNull(account.pending());
        // the migration can run again without duplicates (rows are only added once)
        assertEquals(1, tx(c -> store.allKeys(c)).stream().filter(r -> r.owner().equals(oldPlayer)).count());
    }

    @Test
    void newPlayersGetProfileOneOnFirstJoin() {
        ProfileStore.Account account = tx(c -> store.loadOrCreate(c, newPlayer, 1_000));
        assertEquals(newPlayer, account.active());
        assertEquals(newPlayer, account.profiles().getFirst().id());
        ProfileStore.Account again = tx(c -> store.loadOrCreate(c, newPlayer, 2_000));
        assertEquals(account.profiles(), again.profiles());
    }

    @Test
    void createUsesRandomIdsAndOneProfilePerSlot() {
        tx(c -> store.loadOrCreate(c, oldPlayer, 1_000));
        Profile second = tx(c -> store.create(c, oldPlayer, 2, 2_000));
        assertNotEquals(oldPlayer, second.id());
        assertEquals(oldPlayer, second.owner());
        ProfileStore.Refused used = assertThrows(ProfileStore.Refused.class, () -> rethrow(() -> tx(c -> store.create(c, oldPlayer, 2, 3_000))));
        assertEquals("slot-used", used.key);
        ProfileStore.Refused invalid = assertThrows(ProfileStore.Refused.class, () -> rethrow(() -> tx(c -> store.create(c, oldPlayer, 4, 3_000))));
        assertEquals("invalid-slot", invalid.key);
    }

    @Test
    void switchingSavesTheOldStateAndSeparatesSettings() {
        tx(c -> store.loadOrCreate(c, oldPlayer, 1_000));
        Profile second = tx(c -> store.create(c, oldPlayer, 2, 2_000));
        StoredState mine = new StoredState(new byte[]{1, 2, 3}, new byte[]{9}, "level: 30");
        Map<String, String> settings = Map.of("setting.pm", "false", "vanished", "true");

        ProfileStore.Switched s = tx(c -> store.switchTo(c, oldPlayer, oldPlayer, second.id(), mine, settings,
                k -> k.startsWith("setting."), 60_000, 3_000));
        assertTrue(s.target().fresh(), "a new profile starts empty");
        assertEquals(Map.of("vanished", "true"), s.settings(), "account settings stay, profile settings are separate");
        assertEquals(second.id(), tx(c -> store.activeProfile(c, oldPlayer)).orElseThrow());
        assertEquals(65_000, s.from().playtimeMillis());

        // the fresh row stays until it was applied in game (crash safety), then it is consumed
        assertTrue(tx(c -> store.state(c, second.id())).isPresent());
        tx(c -> {
            store.consumeState(c, oldPlayer, second.id());
            return null;
        });
        assertTrue(tx(c -> store.state(c, second.id())).isEmpty());

        // back to profile 1: its inventory and settings come back, profile 2's settings are stored
        ProfileStore.Switched back = tx(c -> store.switchTo(c, oldPlayer, second.id(), oldPlayer, new StoredState(new byte[]{7}, null, "x: 1"),
                Map.of("vanished", "true", "setting.pm", "true"), k -> k.startsWith("setting."), 0, 4_000));
        assertArrayEquals(new byte[]{1, 2, 3}, back.target().inventory());
        assertArrayEquals(new byte[]{9}, back.target().enderChest());
        assertEquals("level: 30", back.target().data());
        assertEquals(Map.of("vanished", "true", "setting.pm", "false"), back.settings());
        assertEquals(Map.of("setting.pm", "true"), tx(c -> store.settings(c, second.id())));
        String stored = tx(c -> Sql.queryOne(c, "SELECT settings FROM players WHERE uuid = ?", rs -> rs.getString(1), oldPlayer)).orElseThrow();
        assertTrue(stored.contains("\"setting.pm\":\"false\""));
    }

    @Test
    void aSwitchFromAStaleProfileIsRefused() {
        tx(c -> store.loadOrCreate(c, oldPlayer, 1_000));
        Profile second = tx(c -> store.create(c, oldPlayer, 2, 2_000));
        ProfileStore.Refused r = assertThrows(ProfileStore.Refused.class, () -> rethrow(() -> tx(c -> store.switchTo(c, oldPlayer, second.id(),
                oldPlayer, StoredState.FRESH, Map.of(), k -> false, 0, 3_000))));
        assertEquals("changed", r.key);
        ProfileStore.Refused foreign = assertThrows(ProfileStore.Refused.class, () -> rethrow(() -> tx(c -> {
            store.loadOrCreate(c, newPlayer, 1);
            return store.switchTo(c, oldPlayer, oldPlayer, newPlayer, StoredState.FRESH, Map.of(), k -> false, 0, 3_000);
        })));
        assertEquals("gone", foreign.key, "nobody can switch into another player's profile");
    }

    @Test
    void pendingStateIsLoadedAtLoginWhenASwitchWasNotApplied() {
        tx(c -> store.loadOrCreate(c, oldPlayer, 1_000));
        Profile second = tx(c -> store.create(c, oldPlayer, 2, 2_000));
        tx(c -> store.switchTo(c, oldPlayer, oldPlayer, second.id(), new StoredState(new byte[]{1}, null, null), Map.of(), k -> false, 0, 3_000));
        // crash before the new (empty) state was applied: the next login applies it
        ProfileStore.Account account = tx(c -> store.loadOrCreate(c, oldPlayer, 4_000));
        assertEquals(second.id(), account.active());
        assertTrue(account.pending() != null && account.pending().fresh());
    }

    @Test
    void deleteOnlyInactiveProfilesAndIdsAreNeverReused() {
        tx(c -> store.loadOrCreate(c, oldPlayer, 1_000));
        Profile second = tx(c -> store.create(c, oldPlayer, 2, 2_000));
        ProfileStore.Refused active = assertThrows(ProfileStore.Refused.class, () -> rethrow(() -> tx(c -> store.delete(c, oldPlayer, oldPlayer, 3_000))));
        assertEquals("active", active.key);
        tx(c -> store.switchTo(c, oldPlayer, oldPlayer, second.id(), StoredState.FRESH, Map.of(), k -> false, 0, 3_000));
        Profile deleted = tx(c -> store.delete(c, oldPlayer, oldPlayer, 4_000));
        assertEquals(oldPlayer, deleted.id());
        assertTrue(tx(c -> store.state(c, oldPlayer)).isEmpty(), "the deleted profile's inventory is gone");
        Profile recreated = tx(c -> store.create(c, oldPlayer, 1, 5_000));
        assertNotEquals(oldPlayer, recreated.id(), "the player UUID key is never used again after a delete");
        assertTrue(tx(c -> store.allKeys(c)).stream().anyMatch(r -> r.id().equals(oldPlayer) && r.deleted()));
        assertEquals(2, tx(c -> store.liveProfiles(c, oldPlayer)).size());
    }

    @Test
    void slotUnlocksAndSeasonReset() {
        tx(c -> store.loadOrCreate(c, oldPlayer, 1_000));
        assertTrue((boolean) tx(c -> store.addUnlock(c, oldPlayer, 2, SlotSource.GEMS, 1, 2_000)));
        assertFalse((boolean) tx(c -> store.addUnlock(c, oldPlayer, 2, SlotSource.GEMS, 1, 2_000)), "a slot is bought once");
        assertTrue((boolean) tx(c -> store.addUnlock(c, oldPlayer, 3, SlotSource.GRANT, 1, 2_000)));
        assertEquals(1, (int) tx(c -> store.seasonReset(c)));
        List<SlotRules.Unlock> left = tx(c -> store.unlocks(c, oldPlayer));
        assertEquals(1, left.size());
        assertEquals(SlotSource.GEMS, left.getFirst().source());
        assertEquals(2, left.getFirst().slot());
    }

    @Test
    void playtimeIsPerProfile() {
        tx(c -> store.loadOrCreate(c, oldPlayer, 1_000));
        Profile second = tx(c -> store.create(c, oldPlayer, 2, 2_000));
        tx(c -> {
            store.addPlaytime(c, java.util.Collections.singletonList(new Object[]{1_000L, 3_000L, second.id()}));
            return null;
        });
        List<Profile> live = tx(c -> store.liveProfiles(c, oldPlayer));
        assertEquals(5_000, live.get(0).playtimeMillis());
        assertEquals(1_000, live.get(1).playtimeMillis());
    }

    private interface Body {
        Object run() throws Exception;
    }

    /** Unwraps the database wrapper so the Refused reason can be asserted. */
    private static void rethrow(Body body) throws Exception {
        try {
            body.run();
        } catch (RuntimeException e) {
            Throwable t = Database.cause(e);
            if (t instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }
}

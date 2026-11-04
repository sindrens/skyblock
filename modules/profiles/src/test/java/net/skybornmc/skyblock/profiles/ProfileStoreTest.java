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
        assertTrue((boolean) tx(c -> store.addUnlock(c, oldPlayer, 2, SlotSource.CREDITS, 1, 2_000)));
        assertFalse((boolean) tx(c -> store.addUnlock(c, oldPlayer, 2, SlotSource.CREDITS, 1, 2_000)), "a slot is bought once");
        assertTrue((boolean) tx(c -> store.addUnlock(c, oldPlayer, 3, SlotSource.GRANT, 1, 2_000)));
        assertEquals(1, (int) tx(c -> store.seasonReset(c)));
        List<SlotRules.Unlock> left = tx(c -> store.unlocks(c, oldPlayer));
        assertEquals(1, left.size());
        assertEquals(SlotSource.CREDITS, left.getFirst().source());
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

    // ------------------------------------------------------------------ 4 Nov 2026: island = profile (wipe, snapshot, restore)

    private ProfileStore.WipeInput wipeInput(UUID profile, StoredState captured, Map<String, String> settings, long now) {
        return new ProfileStore.WipeInput(oldPlayer, profile, captured, settings, 0, "kick", "Alex", null, "Sky", 1_234L, 5L,
                captured == null ? null : 3, captured != null, now, WipeRules.expiresAt(now, 14));
    }

    @Test
    void onlineWipeRetiresTheKeyKeepsTheStateAndStartsAFreshProfile() {
        tx(c -> store.loadOrCreate(c, oldPlayer, 1_000));
        StoredState items = new StoredState(new byte[]{4, 2}, new byte[]{1}, "level: 12");
        ProfileStore.Wiped w = tx(c -> store.wipe(c, wipeInput(oldPlayer, items, Map.of("setting.pm", "false", "vanished", "true"), 5_000),
                k -> k.startsWith("setting.")));
        assertEquals(oldPlayer, w.gone().id());
        assertNotEquals(oldPlayer, w.fresh().id(), "the fresh profile gets a new key");
        assertEquals(1, w.fresh().slot());
        assertTrue(w.wasActive());
        assertFalse(w.captureOnJoin(), "online: the items were captured right away");
        assertEquals(Map.of("vanished", "true"), w.settings(), "account settings stay");

        ProfileStore.Account account = tx(c -> store.loadOrCreate(c, oldPlayer, 6_000));
        assertEquals(w.fresh().id(), account.active());
        assertEquals(List.of(w.fresh().id()), account.profiles().stream().map(Profile::id).toList(), "the old key is not live");
        assertTrue(account.pending() != null && account.pending().fresh(), "a crash before applying still starts empty");
        assertNull(account.capture());
        // the old key's items are the snapshot
        assertArrayEquals(new byte[]{4, 2}, tx(c -> store.state(c, oldPlayer)).orElseThrow().inventory());
        assertTrue(tx(c -> store.allKeys(c)).stream().anyMatch(r -> r.id().equals(oldPlayer) && r.deleted()));

        ProfileStore.Snapshot snap = tx(c -> store.snapshot(c, w.snapshotId())).orElseThrow();
        assertEquals("kick", snap.reason());
        assertEquals(1_234L, snap.money());
        assertEquals(3, snap.items());
        assertEquals(WipeRules.SnapshotStatus.OPEN, snap.status(6_000));
        assertTrue(snap.notified());
        assertThrows(ProfileStore.Refused.class, () -> rethrow(() -> tx(c -> store.wipe(c, wipeInput(oldPlayer, items, Map.of(), 7_000),
                k -> false))), "a retired key can't be wiped twice");
    }

    @Test
    void offlineWipeTakesTheItemsOnTheNextJoin() {
        tx(c -> store.loadOrCreate(c, oldPlayer, 1_000));
        ProfileStore.Wiped w = tx(c -> store.wipe(c, wipeInput(oldPlayer, null, null, 5_000), k -> k.startsWith("setting.")));
        assertTrue(w.captureOnJoin(), "the player file still holds the items");
        ProfileStore.Account account = tx(c -> store.loadOrCreate(c, oldPlayer, 6_000));
        assertEquals(oldPlayer, account.capture());
        assertEquals(WipeRules.JoinStep.CAPTURE_THEN_APPLY, WipeRules.joinStep(account.active(), account.capture(), account.pending() != null));
        // settings were read from the database (offline): the profile part stays with the old key
        assertEquals(Map.of("setting.pm", "false"), tx(c -> store.settings(c, oldPlayer)));
        assertEquals(1, tx(c -> store.unnotified(c, oldPlayer)).size(), "told on the next join");

        StoredState file = new StoredState(new byte[]{8}, null, "level: 3");
        assertTrue(tx(c -> store.consumeCapture(c, oldPlayer, oldPlayer, file, 7, 6_500)));
        assertFalse(tx(c -> store.consumeCapture(c, oldPlayer, oldPlayer, StoredState.FRESH, 0, 6_600)), "once only");
        assertArrayEquals(new byte[]{8}, tx(c -> store.state(c, oldPlayer)).orElseThrow().inventory());
        assertEquals(7, tx(c -> store.snapshot(c, w.snapshotId())).orElseThrow().items());
        assertNull(tx(c -> store.loadOrCreate(c, oldPlayer, 7_000)).capture());
        tx(c -> {
            store.markNotified(c, oldPlayer);
            return null;
        });
        assertTrue(tx(c -> store.unnotified(c, oldPlayer)).isEmpty());
    }

    @Test
    void wipingAnInactiveProfileKeepsTheActiveOne() {
        tx(c -> store.loadOrCreate(c, oldPlayer, 1_000));
        Profile second = tx(c -> store.create(c, oldPlayer, 2, 2_000));
        ProfileStore.Wiped w = tx(c -> store.wipe(c, wipeInput(second.id(), null, null, 5_000), k -> false));
        assertFalse(w.wasActive());
        assertFalse(w.captureOnJoin());
        assertEquals(2, w.fresh().slot());
        ProfileStore.Account account = tx(c -> store.loadOrCreate(c, oldPlayer, 6_000));
        assertEquals(oldPlayer, account.active());
        assertNull(account.pending());
        assertEquals(List.of(oldPlayer, w.fresh().id()), account.profiles().stream().map(Profile::id).toList());
    }

    @Test
    void restorePutsTheOldProfileBackAndSnapshotsTheCurrentOne() {
        tx(c -> store.loadOrCreate(c, oldPlayer, 1_000));
        StoredState items = new StoredState(new byte[]{4, 2}, null, "level: 12");
        ProfileStore.Wiped w = tx(c -> store.wipe(c, wipeInput(oldPlayer, items, Map.of(), 5_000), k -> false));
        tx(c -> {
            store.consumeState(c, oldPlayer, w.fresh().id());
            return null;
        });
        // online on the fresh profile in that slot
        StoredState now = new StoredState(new byte[]{9}, null, null);
        ProfileStore.Restored r = tx(c -> store.restore(c, new ProfileStore.RestoreInput(oldPlayer, w.snapshotId(), now, Map.of(), 0, "Mod",
                8_000, WipeRules.expiresAt(8_000, 14)), k -> false));
        assertEquals(oldPlayer, r.restored().id());
        assertEquals(w.fresh().id(), r.replaced().id());
        assertTrue(r.becameActive());
        assertArrayEquals(new byte[]{4, 2}, r.apply().inventory(), "the old items are applied");
        assertTrue(r.replacedSnapshotId() > 0);
        assertEquals(oldPlayer, tx(c -> store.activeProfile(c, oldPlayer)).orElseThrow());
        assertArrayEquals(new byte[]{9}, tx(c -> store.state(c, w.fresh().id())).orElseThrow().inventory(), "the replaced profile is kept");
        assertEquals(WipeRules.SnapshotStatus.RESTORED, tx(c -> store.snapshot(c, w.snapshotId())).orElseThrow().status(9_000));
        ProfileStore.Refused twice = assertThrows(ProfileStore.Refused.class, () -> rethrow(() -> tx(c -> store.restore(c,
                new ProfileStore.RestoreInput(oldPlayer, w.snapshotId(), null, null, 0, "Mod", 9_000, 1), k -> false))));
        assertEquals("restored", twice.key);
        // the restore can be undone the same way
        ProfileStore.Restored undo = tx(c -> store.restore(c, new ProfileStore.RestoreInput(oldPlayer, r.replacedSnapshotId(), null, null, 0,
                "Mod", 9_500, WipeRules.expiresAt(9_500, 14)), k -> false));
        assertEquals(w.fresh().id(), undo.restored().id());
    }

    @Test
    void restoreBeforeTheNextJoinKeepsTheItemsInThePlayerFile() {
        tx(c -> store.loadOrCreate(c, oldPlayer, 1_000));
        ProfileStore.Wiped w = tx(c -> store.wipe(c, wipeInput(oldPlayer, null, null, 5_000), k -> false));
        ProfileStore.Restored r = tx(c -> store.restore(c, new ProfileStore.RestoreInput(oldPlayer, w.snapshotId(), null, null, 0, "Mod",
                6_000, WipeRules.expiresAt(6_000, 14)), k -> false));
        assertTrue(r.becameActive());
        assertNull(r.apply(), "nothing to apply: the file still is the old profile");
        ProfileStore.Account account = tx(c -> store.loadOrCreate(c, oldPlayer, 7_000));
        assertEquals(oldPlayer, account.active());
        assertNull(account.capture());
        assertNull(account.pending());
    }

    @Test
    void expiredSnapshotsArePurgedOnceAndCannotBeRestored() {
        tx(c -> store.loadOrCreate(c, oldPlayer, 1_000));
        ProfileStore.Wiped w = tx(c -> store.wipe(c, wipeInput(oldPlayer, new StoredState(new byte[]{1}, null, null), Map.of(), 5_000), k -> false));
        long later = WipeRules.expiresAt(5_000, 14);
        assertTrue(tx(c -> store.purgeDue(c, later - 1, 10)).isEmpty());
        assertEquals(1, tx(c -> store.purgeDue(c, later, 10)).size());
        ProfileStore.Refused expired = assertThrows(ProfileStore.Refused.class, () -> rethrow(() -> tx(c -> store.restore(c,
                new ProfileStore.RestoreInput(oldPlayer, w.snapshotId(), null, null, 0, "Mod", later, later + 1), k -> false))));
        assertEquals("expired", expired.key);
        assertEquals(oldPlayer, tx(c -> store.claimPurge(c, w.snapshotId(), later)).orElseThrow().id());
        assertTrue(tx(c -> store.claimPurge(c, w.snapshotId(), later)).isEmpty(), "purged once");
        assertTrue(tx(c -> store.state(c, oldPlayer)).isEmpty());
        assertTrue(tx(c -> store.purgeDue(c, later, 10)).isEmpty());
    }

    @Test
    void intentsAreStoredAndRemovedByTheWipe() {
        tx(c -> store.loadOrCreate(c, oldPlayer, 1_000));
        tx(c -> {
            store.intend(c, new ProfileStore.Intent(oldPlayer, oldPlayer, "leave", "Steve", UUID.randomUUID(), "Sky", 2_000));
            return null;
        });
        assertEquals(1, tx(c -> store.intents(c, 2_000, 10)).size());
        assertTrue(tx(c -> store.intents(c, 1_999, 10)).isEmpty());
        assertEquals(1, tx(c -> store.intentsOf(c, oldPlayer)).size());
        tx(c -> store.wipe(c, wipeInput(oldPlayer, StoredState.FRESH, Map.of(), 3_000), k -> false));
        assertTrue(tx(c -> store.intentsOf(c, oldPlayer)).isEmpty(), "the wipe removes its intent in the same transaction");
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

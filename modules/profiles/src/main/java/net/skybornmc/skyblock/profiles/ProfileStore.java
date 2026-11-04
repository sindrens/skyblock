package net.skybornmc.skyblock.profiles;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.profile.Profile;
import net.skybornmc.skyblock.common.profile.SlotRules;
import net.skybornmc.skyblock.common.profile.SlotSource;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * SQL of the profiles module. Every method runs on a database thread inside the caller's transaction. Every change of an
 * account's profiles locks its {@code profile_accounts} row first ({@code FOR UPDATE}), so two switches, a switch and a
 * delete, or two creates of the same player can never interleave.
 */
final class ProfileStore {

    private static final Gson GSON = new Gson();
    private static final Type MAP = new TypeToken<Map<String, String>>() { }.getType();
    private static final String COLUMNS = "id, owner, slot, name, icon, created_at, last_played_at, playtime_ms";

    /**
     * Everything about a player that is loaded at login. {@code capture}: the profile whose items are still in the player
     * file although it is not active (wiped or restored away while offline; {@code profile_captures}), or null.
     */
    record Account(UUID owner, UUID active, List<Profile> profiles, List<SlotRules.Unlock> unlocks, StoredState pending, UUID capture) {

        Optional<Profile> profile(UUID id) {
            return profiles.stream().filter(p -> p.id().equals(id)).findFirst();
        }
    }

    /** A key → owner row for the in-memory map ({@code deleted} keys never count on leaderboards). */
    record KeyRow(UUID id, UUID owner, boolean deleted) {
    }

    /** Result of a switch: the state to apply and the player's full settings map after the swap. */
    record Switched(StoredState target, Map<String, String> settings, Profile from, Profile to) {
    }

    /** Why a change was refused inside the transaction. */
    static final class Refused extends SQLException {
        final String key;

        Refused(String key) {
            super("refused: " + key);
            this.key = key;
        }
    }

    private final Supplier<UUID> randomIds;

    ProfileStore(Supplier<UUID> randomIds) {
        this.randomIds = randomIds;
    }

    ProfileStore() {
        this(UUID::randomUUID);
    }

    // ------------------------------------------------------------------ loading

    /**
     * Loads the account at login and creates it on the first join: profile 1 whose id is the player UUID. An account
     * whose active profile is gone (should never happen) is moved to its lowest live profile, or gets a new one.
     */
    Account loadOrCreate(Connection c, UUID owner, long now) throws SQLException {
        Optional<UUID> active = lockAccount(c, owner);
        List<Profile> live = liveProfiles(c, owner);
        if (active.isEmpty()) {
            if (live.isEmpty()) {
                live = List.of(insertProfile(c, owner, 1, now));
            }
            Sql.update(c, "INSERT INTO profile_accounts (owner, active_profile, switched_at) VALUES (?, ?, 0)", owner, live.getFirst().id());
            active = Optional.of(live.getFirst().id());
        } else {
            UUID current = active.get();
            if (live.stream().noneMatch(p -> p.id().equals(current))) {
                if (live.isEmpty()) {
                    live = List.of(insertProfile(c, owner, 1, now));
                }
                Sql.update(c, "UPDATE profile_accounts SET active_profile = ? WHERE owner = ?", live.getFirst().id(), owner);
                active = Optional.of(live.getFirst().id());
            }
        }
        return new Account(owner, active.get(), live, unlocks(c, owner), state(c, active.get()).orElse(null),
                capture(c, owner).orElse(null));
    }

    /** All profile keys with their owners (startup cache). */
    List<KeyRow> allKeys(Connection c) throws SQLException {
        return Sql.query(c, "SELECT id, owner, deleted_at FROM profiles",
                rs -> new KeyRow(Sql.uuid(rs, "id"), Sql.uuid(rs, "owner"), Sql.nullableLong(rs, "deleted_at") != null));
    }

    List<Profile> liveProfiles(Connection c, UUID owner) throws SQLException {
        List<Profile> out = new ArrayList<>(Sql.query(c, "SELECT " + COLUMNS + " FROM profiles WHERE owner = ? AND deleted_at IS NULL",
                this::map, owner));
        out.sort(Comparator.comparingInt(Profile::slot).thenComparingLong(Profile::createdAt));
        return out;
    }

    Optional<UUID> activeProfile(Connection c, UUID owner) throws SQLException {
        return Sql.queryOne(c, "SELECT active_profile FROM profile_accounts WHERE owner = ?", rs -> Sql.uuid(rs, "active_profile"), owner);
    }

    /** Stored playtime of a profile (without a running session), or empty when there is no such profile. */
    Optional<Long> playtime(Connection c, UUID profileId) throws SQLException {
        return Sql.queryOne(c, "SELECT playtime_ms FROM profiles WHERE id = ?", rs -> rs.getLong(1), profileId);
    }

    Optional<StoredState> state(Connection c, UUID profileId) throws SQLException {
        return Sql.queryOne(c, "SELECT inventory, ender_chest, data FROM profile_states WHERE profile_id = ?",
                rs -> new StoredState(rs.getBytes("inventory"), rs.getBytes("ender_chest"), rs.getString("data")), profileId);
    }

    List<SlotRules.Unlock> unlocks(Connection c, UUID owner) throws SQLException {
        return Sql.query(c, "SELECT slot, source, season_id, created_at FROM profile_slots WHERE owner = ? ORDER BY slot", rs ->
                new SlotRules.Unlock(rs.getInt("slot"), SlotSource.byKey(rs.getString("source")).orElse(SlotSource.GRANT),
                        rs.getInt("season_id"), rs.getLong("created_at")), owner);
    }

    // ------------------------------------------------------------------ switching

    /**
     * Saves the current profile's state and makes {@code to} active, in one transaction. A target that was never played
     * gets an empty state row, so a crash before the new state is applied can never leave the old inventory on the new
     * profile (the row is applied on the next join). Settings: the scoped keys of the current map are stored on
     * {@code from}; the result is the map with {@code to}'s scoped keys (also written to {@code players.settings}).
     *
     * @throws Refused {@code changed} (active is not {@code from} any more), {@code gone} (target deleted/foreign),
     *                 {@code same} (already active)
     */
    Switched switchTo(Connection c, UUID owner, UUID from, UUID to, StoredState fromState, Map<String, String> currentSettings,
                      Predicate<String> scoped, long fromPlaytimeAdd, long now) throws SQLException {
        UUID active = lockAccount(c, owner).orElseThrow(() -> new Refused("changed"));
        if (!active.equals(from)) {
            throw new Refused("changed");
        }
        if (from.equals(to)) {
            throw new Refused("same");
        }
        Profile target = liveProfile(c, owner, to).orElseThrow(() -> new Refused("gone"));
        Profile source = liveProfile(c, owner, from).orElseThrow(() -> new Refused("changed"));

        Sql.update(c, "DELETE FROM profile_states WHERE profile_id = ?", from);
        Sql.update(c, "INSERT INTO profile_states (profile_id, owner, inventory, ender_chest, data, saved_at) VALUES (?, ?, ?, ?, ?, ?)",
                from, owner, fromState.inventory(), fromState.enderChest(), fromState.data(), now);
        StoredState targetState = state(c, to).orElse(null);
        if (targetState == null) {
            Sql.update(c, "INSERT INTO profile_states (profile_id, owner, inventory, ender_chest, data, saved_at) VALUES (?, ?, NULL, NULL, NULL, ?)",
                    to, owner, now);
            targetState = StoredState.FRESH;
        }

        Map<String, String> keep = new LinkedHashMap<>();
        Map<String, String> fromScoped = new LinkedHashMap<>();
        currentSettings.forEach((k, v) -> (scoped.test(k) ? fromScoped : keep).put(k, v));
        Map<String, String> toScoped = settings(c, to);
        toScoped.forEach((k, v) -> {
            if (scoped.test(k)) {
                keep.put(k, v);
            }
        });

        long add = Math.max(0, fromPlaytimeAdd);
        Sql.update(c, "UPDATE profiles SET playtime_ms = playtime_ms + ?, last_played_at = ?, settings = ? WHERE id = ?",
                add, now, fromScoped.isEmpty() ? null : GSON.toJson(fromScoped), from);
        Sql.update(c, "UPDATE profiles SET last_played_at = ? WHERE id = ?", now, to);
        Sql.update(c, "UPDATE players SET settings = ? WHERE uuid = ?", keep.isEmpty() ? null : GSON.toJson(keep), owner);
        Sql.update(c, "UPDATE profile_accounts SET active_profile = ?, switched_at = ? WHERE owner = ?", to, now, owner);
        return new Switched(targetState, keep, source.withPlay(now, source.playtimeMillis() + add), target.withPlay(now, target.playtimeMillis()));
    }

    /** The active profile's pending state was applied in game: remove it (only while that profile is still active). */
    void consumeState(Connection c, UUID owner, UUID profileId) throws SQLException {
        Sql.update(c, "DELETE FROM profile_states WHERE profile_id = ? AND EXISTS "
                + "(SELECT 1 FROM profile_accounts a WHERE a.owner = ? AND a.active_profile = ?)", profileId, owner, profileId);
    }

    Map<String, String> settings(Connection c, UUID profileId) throws SQLException {
        String json = Sql.queryOne(c, "SELECT settings FROM profiles WHERE id = ?", rs -> rs.getString("settings"), profileId).orElse(null);
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> map = GSON.fromJson(json, MAP);
        return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
    }

    /** Adds playtime of the running session (autosave and quit). */
    void addPlaytime(Connection c, List<Object[]> rows) throws SQLException {
        if (!rows.isEmpty()) {
            Sql.batch(c, "UPDATE profiles SET playtime_ms = playtime_ms + ?, last_played_at = ? WHERE id = ?", rows);
        }
    }

    // ------------------------------------------------------------------ create, rename, icon, delete

    /**
     * Creates a profile in an empty slot. The first profile an account ever has uses the player UUID as its id; every
     * later one a random id (a deleted id is never reused).
     *
     * @throws Refused {@code slot-used}, {@code invalid-slot}
     */
    Profile create(Connection c, UUID owner, int slot, long now) throws SQLException {
        if (!SlotRules.validSlot(slot)) {
            throw new Refused("invalid-slot");
        }
        lockAccount(c, owner).orElseThrow(() -> new Refused("changed"));
        if (liveProfiles(c, owner).stream().anyMatch(p -> p.slot() == slot)) {
            throw new Refused("slot-used");
        }
        return insertProfile(c, owner, slot, now);
    }

    private Profile insertProfile(Connection c, UUID owner, int slot, long now) throws SQLException {
        boolean ownerKeyUsed = Sql.queryOne(c, "SELECT 1 FROM profiles WHERE id = ?", rs -> 1, owner).isPresent();
        UUID id = ownerKeyUsed ? freshId(c) : owner;
        Sql.update(c, "INSERT INTO profiles (id, owner, slot, name, icon, created_at, last_played_at, playtime_ms, settings, deleted_at) "
                + "VALUES (?, ?, ?, NULL, NULL, ?, 0, 0, NULL, NULL)", id, owner, slot, now);
        return new Profile(id, owner, slot, null, null, now, 0, 0);
    }

    private UUID freshId(Connection c) throws SQLException {
        for (int i = 0; i < 10; i++) {
            UUID id = randomIds.get();
            boolean used = Sql.queryOne(c, "SELECT 1 FROM profiles WHERE id = ?", rs -> 1, id).isPresent()
                    || Sql.queryOne(c, "SELECT 1 FROM players WHERE uuid = ?", rs -> 1, id).isPresent();
            if (!used) {
                return id;
            }
        }
        throw new SQLException("Could not find a free profile id");
    }

    /** @throws Refused {@code gone} */
    void rename(Connection c, UUID owner, UUID id, String name) throws SQLException {
        if (Sql.update(c, "UPDATE profiles SET name = ? WHERE id = ? AND owner = ? AND deleted_at IS NULL", name, id, owner) != 1) {
            throw new Refused("gone");
        }
    }

    /** @throws Refused {@code gone} */
    void setIcon(Connection c, UUID owner, UUID id, String icon) throws SQLException {
        if (Sql.update(c, "UPDATE profiles SET icon = ? WHERE id = ? AND owner = ? AND deleted_at IS NULL", icon, id, owner) != 1) {
            throw new Refused("gone");
        }
    }

    /**
     * Deletes an inactive profile: marks it deleted (the id is never used again) and drops its saved inventory.
     *
     * @throws Refused {@code active} (switch away first), {@code gone}
     */
    Profile delete(Connection c, UUID owner, UUID id, long now) throws SQLException {
        UUID active = lockAccount(c, owner).orElseThrow(() -> new Refused("gone"));
        if (active.equals(id)) {
            throw new Refused("active");
        }
        Profile profile = liveProfile(c, owner, id).orElseThrow(() -> new Refused("gone"));
        Sql.update(c, "UPDATE profiles SET deleted_at = ?, settings = NULL WHERE id = ?", now, id);
        Sql.update(c, "DELETE FROM profile_states WHERE profile_id = ?", id);
        return profile;
    }

    // ------------------------------------------------------------------ wipe, snapshots, restore (4 Nov 2026)

    /** One row of {@code profile_snapshots}. */
    record Snapshot(long id, UUID owner, UUID profileId, UUID replacedBy, int slot, String reason, String actorName, UUID islandId,
                    String islandName, Long money, Long shards, Integer items, long createdAt, long expiresAt, Long restoredAt,
                    String restoredBy, Long purgedAt, boolean notified) {

        WipeRules.SnapshotStatus status(long now) {
            return WipeRules.status(restoredAt, purgedAt, expiresAt, now);
        }
    }

    /** A wipe intent ({@code profile_wipe_intents}). */
    record Intent(UUID profileId, UUID owner, String reason, String actorName, UUID islandId, String islandName, long createdAt) {
    }

    /**
     * What a wipe needs. {@code captured} is the current state of an <b>online</b> player whose active profile is wiped
     * (null otherwise); {@code currentSettings} the cached settings of that online player (null = read the database).
     */
    record WipeInput(UUID owner, UUID profileId, StoredState captured, Map<String, String> currentSettings, long playtimeAdd,
                     String reason, String actorName, UUID islandId, String islandName, Long money, Long shards, Integer items,
                     boolean notified, long now, long expiresAt) {
    }

    /**
     * Result of a wipe: the retired profile, its fresh replacement, whether it was the active one, whether the next join
     * must take the items from the player file ({@code captureOnJoin}), the snapshot id and the settings map that stays
     * on the account (online active wipes put it into the cached player data).
     */
    record Wiped(Profile gone, Profile fresh, boolean wasActive, boolean captureOnJoin, long snapshotId, Map<String, String> settings) {
    }

    /**
     * Wipes one live profile in one transaction: retires its key (deleted_at set; every module's rows stay as the
     * snapshot), creates a fresh profile in the same slot and lists the snapshot. When it was the active profile, the fresh
     * one becomes active with an empty pending state (so a crash before it is applied in game never leaves the old items
     * on the new profile), the old profile's scoped settings are kept on its row and, for an online player, the captured
     * state is saved as the old profile's state; for an offline player the next join takes the items out of the player
     * file ({@code profile_captures}). Removes the profile's wipe intent.
     *
     * @throws Refused {@code gone} (not a live profile of this player)
     */
    Wiped wipe(Connection c, WipeInput in, Predicate<String> scoped) throws SQLException {
        UUID owner = in.owner();
        long now = in.now();
        UUID active = lockAccount(c, owner).orElseThrow(() -> new Refused("gone"));
        Profile profile = liveProfile(c, owner, in.profileId()).orElseThrow(() -> new Refused("gone"));
        boolean wasActive = active.equals(profile.id());
        boolean online = in.captured() != null;
        boolean capture = false;
        Map<String, String> keep = new LinkedHashMap<>();
        Profile fresh;
        if (wasActive) {
            Map<String, String> current = in.currentSettings() != null ? in.currentSettings() : playerSettings(c, owner);
            Map<String, String> oldScoped = new LinkedHashMap<>();
            current.forEach((k, v) -> (scoped.test(k) ? oldScoped : keep).put(k, v));
            Sql.update(c, "UPDATE profiles SET deleted_at = ?, playtime_ms = playtime_ms + ?, last_played_at = ?, settings = ? WHERE id = ?",
                    now, Math.max(0, in.playtimeAdd()), now, oldScoped.isEmpty() ? null : GSON.toJson(oldScoped), profile.id());
            if (online) {
                putState(c, profile.id(), owner, in.captured(), now);
            } else {
                capture = WipeRules.needsCapture(true, false, state(c, profile.id()).isPresent(), capture(c, owner).isPresent());
                if (capture) {
                    Sql.update(c, "INSERT INTO profile_captures (owner, profile_id, created_at) VALUES (?, ?, ?)", owner, profile.id(), now);
                }
            }
            fresh = insertProfile(c, owner, profile.slot(), now);
            Sql.update(c, "UPDATE profile_accounts SET active_profile = ?, switched_at = ? WHERE owner = ?", fresh.id(), now, owner);
            Sql.update(c, "INSERT INTO profile_states (profile_id, owner, inventory, ender_chest, data, saved_at) VALUES (?, ?, NULL, NULL, NULL, ?)",
                    fresh.id(), owner, now);
            Sql.update(c, "UPDATE players SET settings = ? WHERE uuid = ?", keep.isEmpty() ? null : GSON.toJson(keep), owner);
        } else {
            Sql.update(c, "UPDATE profiles SET deleted_at = ? WHERE id = ?", now, profile.id());
            fresh = insertProfile(c, owner, profile.slot(), now);
        }
        Sql.update(c, "DELETE FROM profile_wipe_intents WHERE profile_id = ?", profile.id());
        long snapshotId = insertSnapshot(c, owner, profile.id(), fresh.id(), profile.slot(), in.reason(), in.actorName(), in.islandId(),
                in.islandName(), in.money(), in.shards(), in.items(), now, in.expiresAt(), in.notified());
        return new Wiped(profile, fresh, wasActive, capture, snapshotId, keep);
    }

    /**
     * The join took the items out of the player file: saves them as {@code profileId}'s state and removes the capture
     * row, only while the row still points at that profile. Fills the item count of its open snapshot.
     */
    boolean consumeCapture(Connection c, UUID owner, UUID profileId, StoredState state, int items, long now) throws SQLException {
        lockAccount(c, owner);
        Optional<UUID> marker = capture(c, owner);
        if (marker.isEmpty() || !marker.get().equals(profileId)) {
            return false;
        }
        putState(c, profileId, owner, state, now);
        Sql.update(c, "DELETE FROM profile_captures WHERE owner = ?", owner);
        Sql.update(c, "UPDATE profile_snapshots SET items = ? WHERE profile_id = ? AND items IS NULL", items, profileId);
        return true;
    }

    /** A capture row that points at the active profile is stale: remove it (only if it still points there). */
    void dropCapture(Connection c, UUID owner, UUID profileId) throws SQLException {
        Sql.update(c, "DELETE FROM profile_captures WHERE owner = ? AND profile_id = ?", owner, profileId);
    }

    Optional<UUID> capture(Connection c, UUID owner) throws SQLException {
        return Sql.queryOne(c, "SELECT profile_id FROM profile_captures WHERE owner = ?", rs -> Sql.uuid(rs, "profile_id"), owner);
    }

    /** What a restore needs; {@code captured}/{@code currentSettings} as in {@link WipeInput} (online and the slot is active). */
    record RestoreInput(UUID owner, long snapshotId, StoredState captured, Map<String, String> currentSettings, long playtimeAdd,
                        String actorName, long now, long expiresAt) {
    }

    /**
     * Result of a restore. {@code apply}: the state the online player gets now (or the next join applies), null when the
     * player file already holds it or the slot was not active. {@code replacedSnapshotId}: the snapshot of the profile
     * that was in the slot (0 = the slot was empty).
     */
    record Restored(Profile restored, Profile replaced, boolean becameActive, StoredState apply, long replacedSnapshotId,
                    Snapshot snapshot, Map<String, String> settings) {
    }

    /**
     * Puts a wiped profile back into its slot (one transaction). The profile that is in the slot now is retired with its
     * own snapshot (reason {@code restore_replaced}), exactly like a wipe. When that slot is the active one the restored
     * profile becomes active: its saved state is the pending state (applied at once for an online player, on the next join
     * otherwise); if the player file still holds the restored profile's items (wiped while offline, never joined since)
     * the capture row is simply dropped.
     *
     * @throws Refused {@code gone} (unknown snapshot or profile), {@code restored} (done already), {@code expired} (retention
     *                 over), {@code live} (the profile is live)
     */
    Restored restore(Connection c, RestoreInput in, Predicate<String> scoped) throws SQLException {
        UUID owner = in.owner();
        long now = in.now();
        UUID active = lockAccount(c, owner).orElseThrow(() -> new Refused("gone"));
        Snapshot snap = snapshot(c, in.snapshotId()).filter(s -> s.owner().equals(owner)).orElseThrow(() -> new Refused("gone"));
        WipeRules.SnapshotStatus status = snap.status(now);
        if (status == WipeRules.SnapshotStatus.RESTORED) {
            throw new Refused("restored");
        }
        if (status != WipeRules.SnapshotStatus.OPEN) {
            throw new Refused("expired");
        }
        Optional<Profile> retired = Sql.queryOne(c, "SELECT " + COLUMNS + " FROM profiles WHERE id = ? AND owner = ? AND deleted_at IS NOT NULL",
                this::map, snap.profileId(), owner);
        if (retired.isEmpty()) {
            throw new Refused(liveProfile(c, owner, snap.profileId()).isPresent() ? "live" : "gone");
        }
        Profile old = retired.get();
        Optional<Profile> current = liveProfiles(c, owner).stream().filter(p -> p.slot() == snap.slot()).findFirst();
        boolean slotActive = current.isPresent() && current.get().id().equals(active);
        boolean online = in.captured() != null;
        Map<String, String> keep = new LinkedHashMap<>();
        boolean fileHoldsOld = false;
        long replacedSnapshot = 0;
        if (current.isPresent()) {
            Profile cur = current.get();
            if (slotActive) {
                Map<String, String> settingsNow = in.currentSettings() != null ? in.currentSettings() : playerSettings(c, owner);
                Map<String, String> curScoped = new LinkedHashMap<>();
                settingsNow.forEach((k, v) -> (scoped.test(k) ? curScoped : keep).put(k, v));
                Sql.update(c, "UPDATE profiles SET deleted_at = ?, playtime_ms = playtime_ms + ?, last_played_at = ?, settings = ? WHERE id = ?",
                        now, Math.max(0, in.playtimeAdd()), now, curScoped.isEmpty() ? null : GSON.toJson(curScoped), cur.id());
                if (online) {
                    putState(c, cur.id(), owner, in.captured(), now);
                } else {
                    Optional<UUID> marker = capture(c, owner);
                    if (marker.isPresent() && marker.get().equals(old.id())) {
                        fileHoldsOld = true; // wiped while offline and never joined since: the file still has its items
                        Sql.update(c, "DELETE FROM profile_captures WHERE owner = ?", owner);
                    } else if (WipeRules.needsCapture(true, false, state(c, cur.id()).isPresent(), marker.isPresent())) {
                        Sql.update(c, "INSERT INTO profile_captures (owner, profile_id, created_at) VALUES (?, ?, ?)", owner, cur.id(), now);
                    }
                }
            } else {
                Sql.update(c, "UPDATE profiles SET deleted_at = ? WHERE id = ?", now, cur.id());
            }
            // The replaced profile gets its own snapshot; no chat summary for it (staff did this on purpose).
            replacedSnapshot = insertSnapshot(c, owner, cur.id(), old.id(), cur.slot(),
                    net.skybornmc.skyblock.common.profile.WipeReason.RESTORE_REPLACED.key(), in.actorName(), null, null, null, null,
                    null, now, in.expiresAt(), true);
        }
        Sql.update(c, "UPDATE profiles SET deleted_at = NULL, last_played_at = ? WHERE id = ?", slotActive ? now : old.lastPlayedAt(), old.id());
        StoredState apply = null;
        if (slotActive) {
            Sql.update(c, "UPDATE profile_accounts SET active_profile = ?, switched_at = ? WHERE owner = ?", old.id(), now, owner);
            settings(c, old.id()).forEach((k, v) -> {
                if (scoped.test(k)) {
                    keep.put(k, v);
                }
            });
            Sql.update(c, "UPDATE players SET settings = ? WHERE uuid = ?", keep.isEmpty() ? null : GSON.toJson(keep), owner);
            if (!fileHoldsOld) {
                apply = state(c, old.id()).orElse(null);
                if (apply == null) {
                    // No saved state (should not happen): start it empty rather than give it another profile's items.
                    Sql.update(c, "INSERT INTO profile_states (profile_id, owner, inventory, ender_chest, data, saved_at) VALUES (?, ?, NULL, NULL, NULL, ?)",
                            old.id(), owner, now);
                    apply = StoredState.FRESH;
                }
            }
        }
        Sql.update(c, "UPDATE profile_snapshots SET restored_at = ?, restored_by = ? WHERE id = ?", now, clip(in.actorName(), 32), snap.id());
        Sql.update(c, "DELETE FROM profile_wipe_intents WHERE profile_id = ?", old.id());
        Profile live = liveProfile(c, owner, old.id()).orElse(old);
        return new Restored(live, current.orElse(null), slotActive, apply, replacedSnapshot, snap, keep);
    }

    Optional<Snapshot> snapshot(Connection c, long id) throws SQLException {
        return Sql.queryOne(c, "SELECT " + SNAPSHOT_COLUMNS + " FROM profile_snapshots WHERE id = ?", this::snapshotRow, id);
    }

    /** The newest snapshots of a player first. */
    List<Snapshot> snapshots(Connection c, UUID owner, int limit) throws SQLException {
        return Sql.query(c, "SELECT " + SNAPSHOT_COLUMNS + " FROM profile_snapshots WHERE owner = ? ORDER BY id DESC LIMIT ?",
                this::snapshotRow, owner, Math.max(1, limit));
    }

    /** Snapshots of a wipe the player was not told about yet (wiped while offline), oldest first. */
    List<Snapshot> unnotified(Connection c, UUID owner) throws SQLException {
        return Sql.query(c, "SELECT " + SNAPSHOT_COLUMNS + " FROM profile_snapshots WHERE owner = ? AND notified = FALSE ORDER BY id",
                this::snapshotRow, owner);
    }

    void markNotified(Connection c, UUID owner) throws SQLException {
        Sql.update(c, "UPDATE profile_snapshots SET notified = TRUE WHERE owner = ? AND notified = FALSE", owner);
    }

    /** Snapshots whose retention ended and that were never restored nor purged (oldest first). */
    List<Snapshot> purgeDue(Connection c, long now, int limit) throws SQLException {
        return Sql.query(c, "SELECT " + SNAPSHOT_COLUMNS + " FROM profile_snapshots WHERE purged_at IS NULL AND restored_at IS NULL "
                + "AND expires_at <= ? ORDER BY expires_at LIMIT ?", this::snapshotRow, now, Math.max(1, limit));
    }

    /**
     * Claims one due snapshot for the purge: marks it purged and drops the old key's saved state and capture row. Returns
     * the retired profile, or empty when it was restored or purged meanwhile (or the key is live again).
     */
    Optional<Profile> claimPurge(Connection c, long snapshotId, long now) throws SQLException {
        Optional<Snapshot> snap = snapshot(c, snapshotId);
        if (snap.isEmpty()) {
            return Optional.empty();
        }
        Optional<Profile> old = Sql.queryOne(c, "SELECT " + COLUMNS + " FROM profiles WHERE id = ? AND deleted_at IS NOT NULL",
                this::map, snap.get().profileId());
        if (old.isEmpty()) {
            return Optional.empty();
        }
        if (Sql.update(c, "UPDATE profile_snapshots SET purged_at = ? WHERE id = ? AND purged_at IS NULL AND restored_at IS NULL "
                + "AND expires_at <= ?", now, snapshotId, now) != 1) {
            return Optional.empty();
        }
        Sql.update(c, "DELETE FROM profile_states WHERE profile_id = ?", old.get().id());
        Sql.update(c, "DELETE FROM profile_captures WHERE profile_id = ?", old.get().id());
        return old;
    }

    /** Records a wipe intent (replaces an older one of the same profile). */
    void intend(Connection c, Intent intent) throws SQLException {
        Sql.update(c, "DELETE FROM profile_wipe_intents WHERE profile_id = ?", intent.profileId());
        Sql.update(c, "INSERT INTO profile_wipe_intents (profile_id, owner, reason, actor_name, island_id, island_name, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", intent.profileId(), intent.owner(), clip(intent.reason(), 24),
                clip(intent.actorName() == null ? "?" : intent.actorName(), 32), intent.islandId(), clip(intent.islandName(), 64),
                intent.createdAt());
    }

    void dropIntent(Connection c, UUID profileId) throws SQLException {
        Sql.update(c, "DELETE FROM profile_wipe_intents WHERE profile_id = ?", profileId);
    }

    /** Intents written before {@code before} (crash leftovers), oldest first. */
    List<Intent> intents(Connection c, long before, int limit) throws SQLException {
        return Sql.query(c, "SELECT " + INTENT_COLUMNS + " FROM profile_wipe_intents WHERE created_at <= ? ORDER BY created_at LIMIT ?",
                this::intentRow, before, Math.max(1, limit));
    }

    /** Intents of one player (checked at join). */
    List<Intent> intentsOf(Connection c, UUID owner) throws SQLException {
        return Sql.query(c, "SELECT " + INTENT_COLUMNS + " FROM profile_wipe_intents WHERE owner = ? ORDER BY created_at",
                this::intentRow, owner);
    }

    private static final String SNAPSHOT_COLUMNS = "id, owner, profile_id, replaced_by, slot, reason, actor_name, island_id, island_name, "
            + "money, shards, items, created_at, expires_at, restored_at, restored_by, purged_at, notified";
    private static final String INTENT_COLUMNS = "profile_id, owner, reason, actor_name, island_id, island_name, created_at";

    private long insertSnapshot(Connection c, UUID owner, UUID profileId, UUID replacedBy, int slot, String reason, String actorName,
                                UUID islandId, String islandName, Long money, Long shards, Integer items, long now, long expiresAt,
                                boolean notified) throws SQLException {
        return Sql.insertReturningId(c, "INSERT INTO profile_snapshots (owner, profile_id, replaced_by, slot, reason, actor_name, island_id, "
                        + "island_name, money, shards, items, created_at, expires_at, restored_at, restored_by, purged_at, notified) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, ?)",
                owner, profileId, replacedBy, slot, clip(reason, 24), clip(actorName == null ? "?" : actorName, 32), islandId,
                clip(islandName, 64), money, shards, items, now, expiresAt, notified);
    }

    private void putState(Connection c, UUID profileId, UUID owner, StoredState state, long now) throws SQLException {
        Sql.update(c, "DELETE FROM profile_states WHERE profile_id = ?", profileId);
        Sql.update(c, "INSERT INTO profile_states (profile_id, owner, inventory, ender_chest, data, saved_at) VALUES (?, ?, ?, ?, ?, ?)",
                profileId, owner, state.inventory(), state.enderChest(), state.data(), now);
    }

    private Map<String, String> playerSettings(Connection c, UUID owner) throws SQLException {
        String json = Sql.queryOne(c, "SELECT settings FROM players WHERE uuid = ?", rs -> rs.getString("settings"), owner).orElse(null);
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> map = GSON.fromJson(json, MAP);
        return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
    }

    private Snapshot snapshotRow(ResultSet rs) throws SQLException {
        int items = rs.getInt("items");
        Integer itemCount = rs.wasNull() ? null : items;
        return new Snapshot(rs.getLong("id"), Sql.uuid(rs, "owner"), Sql.uuid(rs, "profile_id"), nullableUuid(rs, "replaced_by"),
                rs.getInt("slot"), rs.getString("reason"), rs.getString("actor_name"), nullableUuid(rs, "island_id"),
                rs.getString("island_name"), Sql.nullableLong(rs, "money"), Sql.nullableLong(rs, "shards"), itemCount,
                rs.getLong("created_at"), rs.getLong("expires_at"), Sql.nullableLong(rs, "restored_at"), rs.getString("restored_by"),
                Sql.nullableLong(rs, "purged_at"), rs.getBoolean("notified"));
    }

    private Intent intentRow(ResultSet rs) throws SQLException {
        return new Intent(Sql.uuid(rs, "profile_id"), Sql.uuid(rs, "owner"), rs.getString("reason"), rs.getString("actor_name"),
                nullableUuid(rs, "island_id"), rs.getString("island_name"), rs.getLong("created_at"));
    }

    private static UUID nullableUuid(ResultSet rs, String column) throws SQLException {
        byte[] bytes = rs.getBytes(column);
        return bytes == null ? null : Sql.toUuid(bytes);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    // ------------------------------------------------------------------ slots

    /** Stores a slot unlock once; false if the slot was unlocked already. */
    boolean addUnlock(Connection c, UUID owner, int slot, SlotSource source, int seasonId, long now) throws SQLException {
        if (Sql.queryOne(c, "SELECT 1 FROM profile_slots WHERE owner = ? AND slot = ?", rs -> 1, owner, slot).isPresent()) {
            return false;
        }
        Sql.update(c, "INSERT INTO profile_slots (owner, slot, source, season_id, created_at) VALUES (?, ?, ?, ?, ?)",
                owner, slot, source.key(), seasonId, now);
        return true;
    }

    /** Owner decision P8: a season reset keeps credit-bought slots only. Returns the number of removed unlocks. */
    int seasonReset(Connection c) throws SQLException {
        return Sql.update(c, "DELETE FROM profile_slots WHERE source <> ?", SlotSource.CREDITS.key());
    }

    // ------------------------------------------------------------------ helpers

    private Optional<UUID> lockAccount(Connection c, UUID owner) throws SQLException {
        return Sql.queryOne(c, "SELECT active_profile FROM profile_accounts WHERE owner = ? FOR UPDATE", rs -> Sql.uuid(rs, "active_profile"), owner);
    }

    private Optional<Profile> liveProfile(Connection c, UUID owner, UUID id) throws SQLException {
        return Sql.queryOne(c, "SELECT " + COLUMNS + " FROM profiles WHERE id = ? AND owner = ? AND deleted_at IS NULL", this::map, id, owner);
    }

    private Profile map(ResultSet rs) throws SQLException {
        return new Profile(Sql.uuid(rs, "id"), Sql.uuid(rs, "owner"), rs.getInt("slot"), rs.getString("name"), rs.getString("icon"),
                rs.getLong("created_at"), rs.getLong("last_played_at"), rs.getLong("playtime_ms"));
    }
}

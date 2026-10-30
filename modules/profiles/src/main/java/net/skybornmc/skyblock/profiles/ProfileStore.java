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

    /** Everything about a player that is loaded at login. */
    record Account(UUID owner, UUID active, List<Profile> profiles, List<SlotRules.Unlock> unlocks, StoredState pending) {

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
        return new Account(owner, active.get(), live, unlocks(c, owner), state(c, active.get()).orElse(null));
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

    /** Owner decision P8: a season reset keeps gem-bought slots only. Returns the number of removed unlocks. */
    int seasonReset(Connection c) throws SQLException {
        return Sql.update(c, "DELETE FROM profile_slots WHERE source <> ?", SlotSource.GEMS.key());
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

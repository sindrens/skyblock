package net.skybornmc.skyblock.common.player;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.skybornmc.skyblock.common.db.Sql;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** SQL for {@code players} and {@code name_history}. All methods run on a database thread. */
public final class PlayerRepository {

    private static final Gson GSON = new Gson();
    private static final Type SETTINGS_TYPE = new TypeToken<Map<String, String>>() { }.getType();
    private static final String COLUMNS = "uuid, name, first_seen, last_seen, playtime_ms, bedrock, discord_id, settings";

    /** Result of a login load. */
    public record Loaded(PlayerData.Snapshot snapshot, boolean isNew, String previousName) {
    }

    /**
     * Loads the player or creates the row on first join. Records a name change in {@code name_history}.
     * Runs in one transaction.
     */
    public Loaded loadOrCreate(Connection c, UUID uuid, String name, long now) throws SQLException {
        Optional<PlayerData.Snapshot> existing = Sql.queryOne(c, "SELECT " + COLUMNS + " FROM players WHERE uuid = ?", this::map, uuid);
        if (existing.isEmpty()) {
            PlayerData.Snapshot fresh = new PlayerData.Snapshot(uuid, name, now, now, 0, false, null, Map.of());
            Sql.update(c, "INSERT INTO players (uuid, name, name_lower, first_seen, last_seen, playtime_ms, bedrock, discord_id, settings) "
                    + "VALUES (?, ?, ?, ?, ?, 0, FALSE, NULL, NULL)", uuid, name, lower(name), now, now);
            addHistory(c, uuid, name, now);
            return new Loaded(fresh, true, null);
        }
        PlayerData.Snapshot s = existing.get();
        String previous = null;
        if (!s.name().equals(name)) {
            previous = s.name();
            Sql.update(c, "UPDATE players SET name = ?, name_lower = ? WHERE uuid = ?", name, lower(name), uuid);
            addHistory(c, uuid, name, now);
            s = new PlayerData.Snapshot(uuid, name, s.firstSeen(), s.lastSeen(), s.playtimeMillis(), s.bedrock(), s.discordId(), s.settings());
        }
        return new Loaded(s, false, previous);
    }

    private void addHistory(Connection c, UUID uuid, String name, long now) throws SQLException {
        Sql.update(c, "INSERT INTO name_history (uuid, name, name_lower, first_used) VALUES (?, ?, ?, ?)", uuid, name, lower(name), now);
    }

    /** Writes all fields of the given snapshots in one batch. */
    public void save(Connection c, List<PlayerData.Snapshot> snapshots) throws SQLException {
        Sql.batch(c, "UPDATE players SET name = ?, name_lower = ?, last_seen = ?, playtime_ms = ?, bedrock = ?, discord_id = ?, settings = ? "
                        + "WHERE uuid = ?",
                snapshots.stream().map(s -> new Object[]{s.name(), lower(s.name()), s.lastSeen(), s.playtimeMillis(), s.bedrock(),
                        s.discordId(), s.settings().isEmpty() ? null : GSON.toJson(s.settings()), s.uuid()}).toList());
    }

    /** Current holder of a name (case-insensitive), most recently seen first. */
    public Optional<PlayerData.Snapshot> findByName(Connection c, String name) throws SQLException {
        return Sql.queryOne(c, "SELECT " + COLUMNS + " FROM players WHERE name_lower = ? ORDER BY last_seen DESC", this::map, lower(name));
    }

    public Optional<PlayerData.Snapshot> findByUuid(Connection c, UUID uuid) throws SQLException {
        return Sql.queryOne(c, "SELECT " + COLUMNS + " FROM players WHERE uuid = ?", this::map, uuid);
    }

    /** All names a player used, oldest first (for staff lookups). */
    public List<NameRecord> nameHistory(Connection c, UUID uuid) throws SQLException {
        return Sql.query(c, "SELECT name, first_used FROM name_history WHERE uuid = ? ORDER BY first_used",
                rs -> new NameRecord(rs.getString("name"), rs.getLong("first_used")), uuid);
    }

    /** Names of the most recently seen players (offline-name tab completion). */
    public List<String> recentNames(Connection c, int limit) throws SQLException {
        return Sql.query(c, "SELECT name FROM players ORDER BY last_seen DESC LIMIT ?", rs -> rs.getString(1), limit);
    }

    /** One entry of a player's name history. */
    public record NameRecord(String name, long firstUsed) {
    }

    private PlayerData.Snapshot map(ResultSet rs) throws SQLException {
        String json = rs.getString("settings");
        Map<String, String> settings = json == null || json.isBlank() ? Map.of() : GSON.fromJson(json, SETTINGS_TYPE);
        return new PlayerData.Snapshot(Sql.uuid(rs, "uuid"), rs.getString("name"), rs.getLong("first_seen"), rs.getLong("last_seen"),
                rs.getLong("playtime_ms"), rs.getBoolean("bedrock"), Sql.nullableLong(rs, "discord_id"),
                settings == null ? Map.of() : settings);
    }

    static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}

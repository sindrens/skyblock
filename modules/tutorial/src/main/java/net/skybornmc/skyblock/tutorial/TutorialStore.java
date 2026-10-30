package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.tutorial.TutorialService.State;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL of the tutorial tables ({@code db/tutorial}). Every method takes the caller's connection (one transaction where
 * it matters); no reserved words. Unit-tested on H2 in MySQL mode.
 */
final class TutorialStore {

    private static final String COLUMNS = "progress_state, step_id, step_started_at, started_at, finished_at, replays, flow_version, "
            + "pack_given, rewarded, chest_world, chest_x, chest_y, chest_z";

    /** The account's progress with its done steps and counters, or empty when the account has no row. */
    Optional<Progress> load(Connection c, UUID player) throws SQLException {
        Optional<Progress> found = Sql.queryOne(c, "SELECT " + COLUMNS + " FROM tutorial_progress WHERE player_uuid = ?", rs -> {
            Progress p = new Progress(player, state(rs.getString("progress_state")));
            p.step = rs.getString("step_id");
            p.stepStartedAt = rs.getLong("step_started_at");
            p.startedAt = Sql.nullableLong(rs, "started_at");
            p.finishedAt = Sql.nullableLong(rs, "finished_at");
            p.replays = rs.getInt("replays");
            p.flowVersion = rs.getInt("flow_version");
            p.packGiven = rs.getBoolean("pack_given");
            p.rewarded = rs.getBoolean("rewarded");
            String world = rs.getString("chest_world");
            p.chest = world == null ? null : world + ":" + rs.getInt("chest_x") + "," + rs.getInt("chest_y") + "," + rs.getInt("chest_z");
            return p;
        }, player);
        if (found.isEmpty()) {
            return found;
        }
        Progress p = found.get();
        for (Object[] row : Sql.query(c, "SELECT step_id, early FROM tutorial_step_done WHERE player_uuid = ? ORDER BY done_at",
                rs -> new Object[]{rs.getString("step_id"), rs.getBoolean("early")}, player)) {
            p.done.put((String) row[0], (Boolean) row[1]);
        }
        for (Object[] row : Sql.query(c, "SELECT step_id, amount FROM tutorial_counts WHERE player_uuid = ?",
                rs -> new Object[]{rs.getString("step_id"), rs.getInt("amount")}, player)) {
            p.counts.put((String) row[0], (Integer) row[1]);
        }
        p.optional.putAll(loadOptional(c, player));
        return found;
    }

    // ------------------------------------------------------------------ optional steps (V003)

    /** The account's optional steps (tipped, done, rewarded, progress). */
    java.util.Map<String, OptionalRules.Entry> loadOptional(Connection c, UUID player) throws SQLException {
        java.util.Map<String, OptionalRules.Entry> out = new java.util.HashMap<>();
        for (Object[] row : Sql.query(c, "SELECT step_id, tipped_at, done_at, rewarded, progress_value FROM tutorial_optional WHERE player_uuid = ?",
                rs -> new Object[]{rs.getString("step_id"), rs.getLong("tipped_at"), rs.getLong("done_at"), rs.getBoolean("rewarded"),
                        rs.getInt("progress_value")}, player)) {
            OptionalRules.Entry e = new OptionalRules.Entry();
            e.tippedAt = (Long) row[1];
            e.doneAt = (Long) row[2];
            e.rewarded = (Boolean) row[3];
            e.value = (Integer) row[4];
            out.put((String) row[0], e);
        }
        return out;
    }

    private void ensureOptional(Connection c, UUID player, String step) throws SQLException {
        if (Sql.queryOne(c, "SELECT 1 AS hit FROM tutorial_optional WHERE player_uuid = ? AND step_id = ?", rs -> 1, player, step).isPresent()) {
            return;
        }
        try {
            Sql.update(c, "INSERT INTO tutorial_optional (player_uuid, step_id) VALUES (?, ?)", player, step);
        } catch (SQLException e) {
            if (!duplicate(e)) {
                throw e;
            }
        }
    }

    /** Remembers that the first-visit tip of an optional step was shown (never shown again). */
    void markTipped(Connection c, UUID player, String step, long at) throws SQLException {
        ensureOptional(c, player, step);
        Sql.update(c, "UPDATE tutorial_optional SET tipped_at = ? WHERE player_uuid = ? AND step_id = ? AND tipped_at = 0", at, player, step);
    }

    /** Saves an optional step's progress (blocks broken or visited mask) while it is not done. */
    void setOptionalValue(Connection c, UUID player, String step, int value) throws SQLException {
        ensureOptional(c, player, step);
        Sql.update(c, "UPDATE tutorial_optional SET progress_value = ? WHERE player_uuid = ? AND step_id = ? AND done_at = 0", value, player, step);
    }

    /**
     * The "done once" guard of an optional step, run inside the reward's transaction: {@code done_at 0 → now} must hit
     * exactly one row, so the crystals can never be paid twice (two events in one tick, two servers).
     */
    boolean completeOptional(Connection c, UUID player, String step, long at, boolean rewarded) throws SQLException {
        ensureOptional(c, player, step);
        return Sql.update(c, "UPDATE tutorial_optional SET done_at = ?, rewarded = ? WHERE player_uuid = ? AND step_id = ? AND done_at = 0",
                at, rewarded, player, step) == 1;
    }

    // ------------------------------------------------------------------ analytics (TU7)

    /** Events of the stats period (newest last), for {@link FunnelStats}. */
    java.util.List<FunnelStats.Event> events(Connection c, long since, int limit) throws SQLException {
        return Sql.query(c, "SELECT player_uuid, step_id, event_kind, created_at, bedrock, millis_in_step FROM tutorial_events "
                        + "WHERE created_at >= ? ORDER BY created_at LIMIT ?",
                rs -> new FunnelStats.Event(Sql.uuid(rs, "player_uuid"), rs.getString("step_id"), rs.getString("event_kind"),
                        rs.getLong("created_at"), rs.getBoolean("bedrock"), rs.getLong("millis_in_step")), since, limit);
    }

    /** Players whose tutorial is running → their current step (drop-off). */
    java.util.Map<UUID, String> activeSteps(Connection c) throws SQLException {
        java.util.Map<UUID, String> out = new java.util.HashMap<>();
        for (Object[] row : Sql.query(c, "SELECT player_uuid, step_id FROM tutorial_progress WHERE progress_state = ?",
                rs -> new Object[]{Sql.uuid(rs, "player_uuid"), rs.getString("step_id")}, key(State.ACTIVE))) {
            out.put((UUID) row[0], (String) row[1]);
        }
        return out;
    }

    /** Deletes events older than {@code before} ({@code analytics.keep-days}); returns how many. */
    int purgeEvents(Connection c, long before) throws SQLException {
        return Sql.update(c, "DELETE FROM tutorial_events WHERE created_at < ?", before);
    }

    /** Loads the account, creating a {@code new} row for a player seen for the first time. */
    Progress loadOrCreate(Connection c, UUID player, int flowVersion) throws SQLException {
        Optional<Progress> found = load(c, player);
        if (found.isPresent()) {
            return found.get();
        }
        try {
            Sql.update(c, "INSERT INTO tutorial_progress (player_uuid, progress_state, step_started_at, flow_version) VALUES (?, ?, 0, ?)",
                    player, key(State.NEW), flowVersion);
        } catch (SQLException e) {
            if (!duplicate(e)) {
                throw e;
            }
        }
        return load(c, player).orElseGet(() -> Progress.fresh(player));
    }

    /** Saves the row (state, step, times, flags). */
    void save(Connection c, Progress p) throws SQLException {
        String[] chest = chestParts(p.chest);
        int updated = Sql.update(c, "UPDATE tutorial_progress SET progress_state = ?, step_id = ?, step_started_at = ?, started_at = ?, "
                        + "finished_at = ?, replays = ?, flow_version = ?, pack_given = ?, rewarded = ?, chest_world = ?, chest_x = ?, "
                        + "chest_y = ?, chest_z = ? WHERE player_uuid = ?",
                key(p.state), p.step, p.stepStartedAt, p.startedAt, p.finishedAt, p.replays, p.flowVersion, p.packGiven, p.rewarded,
                chest[0], intOf(chest[1]), intOf(chest[2]), intOf(chest[3]), p.player);
        if (updated == 0) {
            Sql.update(c, "INSERT INTO tutorial_progress (player_uuid, " + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    p.player, key(p.state), p.step, p.stepStartedAt, p.startedAt, p.finishedAt, p.replays, p.flowVersion, p.packGiven,
                    p.rewarded, chest[0], intOf(chest[1]), intOf(chest[2]), intOf(chest[3]));
        }
    }

    /** Stores a done step (a second call for the same step changes nothing). */
    void markDone(Connection c, UUID player, String step, long at, boolean early, UUID profile) throws SQLException {
        if (Sql.queryOne(c, "SELECT 1 AS hit FROM tutorial_step_done WHERE player_uuid = ? AND step_id = ?", rs -> 1, player, step).isPresent()) {
            return;
        }
        try {
            Sql.update(c, "INSERT INTO tutorial_step_done (player_uuid, step_id, done_at, early, profile_id) VALUES (?, ?, ?, ?, ?)",
                    player, step, at, early, profile);
        } catch (SQLException e) {
            if (!duplicate(e)) {
                throw e;
            }
        }
    }

    void setCount(Connection c, UUID player, String step, int amount) throws SQLException {
        Sql.update(c, "INSERT INTO tutorial_counts (player_uuid, step_id, amount) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE amount = VALUES(amount)", player, step, amount);
    }

    /** Forgets every done step and counter (staff reset / jump; the row itself is saved by {@link #save}). */
    void clearSteps(Connection c, UUID player) throws SQLException {
        Sql.update(c, "DELETE FROM tutorial_step_done WHERE player_uuid = ?", player);
        Sql.update(c, "DELETE FROM tutorial_counts WHERE player_uuid = ?", player);
    }

    void event(Connection c, UUID player, String step, String kind, long at, boolean bedrock, long millisInStep) throws SQLException {
        Sql.update(c, "INSERT INTO tutorial_events (player_uuid, step_id, event_kind, created_at, bedrock, millis_in_step) VALUES (?, ?, ?, ?, ?, ?)",
                player, step, kind, at, bedrock, Math.max(0, millisInStep));
    }

    /**
     * The finish guard, run inside the reward's transaction: {@code active → finished} must hit exactly one row, so a
     * double finish (two events in one tick, two servers) can never pay twice.
     */
    boolean finish(Connection c, UUID player, long now, UUID profile, boolean rewarded) throws SQLException {
        return Sql.update(c, "UPDATE tutorial_progress SET progress_state = ?, step_id = NULL, finished_at = ?, finished_profile = ?, "
                        + "rewarded = ? WHERE player_uuid = ? AND progress_state = ?",
                key(State.FINISHED), now, profile, rewarded, player, key(State.ACTIVE)) == 1;
    }

    /** Atomic "give the Starter pack once per account": true for exactly one caller. */
    boolean claimPack(Connection c, UUID player) throws SQLException {
        return Sql.update(c, "UPDATE tutorial_progress SET pack_given = TRUE WHERE player_uuid = ? AND pack_given = FALSE", player) == 1;
    }

    /** Remembers the starter chest of the account's first island (the chest step points there). */
    void setChest(Connection c, UUID player, String world, int x, int y, int z) throws SQLException {
        Sql.update(c, "UPDATE tutorial_progress SET chest_world = ?, chest_x = ?, chest_y = ?, chest_z = ? WHERE player_uuid = ?",
                world, x, y, z, player);
    }

    // ------------------------------------------------------------------ helpers

    static String key(State state) {
        return state.name().toLowerCase(Locale.ROOT);
    }

    static State state(String key) {
        try {
            return State.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return State.NEW;
        }
    }

    private static String[] chestParts(String chest) {
        if (chest == null) {
            return new String[]{null, "0", "0", "0"};
        }
        int colon = chest.lastIndexOf(':');
        String[] xyz = chest.substring(colon + 1).split(",");
        if (colon <= 0 || xyz.length != 3) {
            return new String[]{null, "0", "0", "0"};
        }
        return new String[]{chest.substring(0, colon), xyz[0], xyz[1], xyz[2]};
    }

    private static int intOf(String s) {
        try {
            return Integer.parseInt(s.strip());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static boolean duplicate(SQLException e) {
        String state = e.getSQLState();
        return state != null && state.startsWith("23") || e.getErrorCode() == 1062 || e.getErrorCode() == 23505;
    }
}

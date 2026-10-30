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
        return found;
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

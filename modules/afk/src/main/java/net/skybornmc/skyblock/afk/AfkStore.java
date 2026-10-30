package net.skybornmc.skyblock.afk;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQL of the AFK pool tables (same statements on MariaDB and H2 in MySQL mode). Callers run it off the main thread. */
final class AfkStore {

    static final String KIND_KEY = "key";
    static final String KIND_FUEL = "fuel";

    /** An undelivered key or fuel grant. {@code ref} = crate id or island UUID. */
    record Grant(long id, UUID player, UUID profileKey, String kind, String ref, long amount) {
    }

    /** A profile's AFK booster: banked time not started yet, and when the running one ends. */
    record Booster(int percent, long bankedMillis, long activeUntil) {

        static final Booster NONE = new Booster(0, 0, 0);

        boolean active(long now) {
            return activeUntil > now && percent > 0;
        }
    }

    // ------------------------------------------------------------------ account state

    /** Loads an account for {@code today} (creates the state row when missing). */
    AfkAccount.Snapshot load(Connection c, UUID player, int today, long now) throws SQLException {
        Optional<long[]> state = Sql.queryOne(c, "SELECT reward_seq, progress_ms FROM afk_state WHERE player_uuid = ?",
                rs -> new long[]{rs.getLong("reward_seq"), rs.getLong("progress_ms")}, player);
        if (state.isEmpty()) {
            Sql.update(c, "INSERT INTO afk_state (player_uuid, reward_seq, progress_ms, updated_at) VALUES (?, 0, 0, ?)", player, now);
        }
        long credited = Sql.queryOne(c, "SELECT credited_ms FROM afk_daily WHERE player_uuid = ? AND day_key = ?",
                rs -> rs.getLong("credited_ms"), player, today).orElse(0L);
        long[] s = state.orElse(new long[]{0, 0});
        return new AfkAccount.Snapshot(player, today, credited, s[1], s[0]);
    }

    /** Saves credited time and reward progress (never the reward number: only {@link #reward} moves it). */
    void save(Connection c, AfkAccount.Snapshot s, long now) throws SQLException {
        Sql.update(c, "UPDATE afk_state SET progress_ms = ?, updated_at = ? WHERE player_uuid = ?", s.progressMillis(), now, s.player());
        saveDaily(c, s, 0, now);
    }

    /**
     * Stores one reward: moves the reward number from {@code seq - 1} to {@code seq} (compare-and-set) together with
     * the progress and today's total. False if the stored number is not {@code seq - 1} (already stored, or a stale
     * copy): the caller's transaction then rolls back, including the money.
     */
    boolean reward(Connection c, AfkAccount.Snapshot s, long seq, long now) throws SQLException {
        int changed = Sql.update(c, "UPDATE afk_state SET reward_seq = ?, progress_ms = ?, updated_at = ? WHERE player_uuid = ? AND reward_seq = ?",
                seq, s.progressMillis(), now, s.player(), seq - 1);
        if (changed == 0) {
            return false;
        }
        saveDaily(c, s, 1, now);
        return true;
    }

    private void saveDaily(Connection c, AfkAccount.Snapshot s, int rewards, long now) throws SQLException {
        if (Sql.update(c, "UPDATE afk_daily SET credited_ms = ?, rewards_today = rewards_today + ?, updated_at = ? WHERE player_uuid = ? AND day_key = ?",
                s.creditedMillis(), rewards, now, s.player(), s.day()) == 0) {
            Sql.update(c, "INSERT INTO afk_daily (player_uuid, day_key, credited_ms, rewards_today, updated_at) VALUES (?, ?, ?, ?, ?)",
                    s.player(), s.day(), s.creditedMillis(), rewards, now);
        }
    }

    /** Today's stored total of an account (staff/tests). */
    long credited(Connection c, UUID player, int day) throws SQLException {
        return Sql.queryOne(c, "SELECT credited_ms FROM afk_daily WHERE player_uuid = ? AND day_key = ?",
                rs -> rs.getLong("credited_ms"), player, day).orElse(0L);
    }

    long rewardSeq(Connection c, UUID player) throws SQLException {
        return Sql.queryOne(c, "SELECT reward_seq FROM afk_state WHERE player_uuid = ?", rs -> rs.getLong("reward_seq"), player).orElse(0L);
    }

    // ------------------------------------------------------------------ key milestones

    /** Marks a key milestone as reached today. False if it was already reached (once per account per day). */
    boolean milestone(Connection c, UUID player, int day, int minutes, long now) throws SQLException {
        if (Sql.queryOne(c, "SELECT milestone_min FROM afk_milestones WHERE player_uuid = ? AND day_key = ? AND milestone_min = ?",
                rs -> rs.getInt(1), player, day, minutes).isPresent()) {
            return false;
        }
        try {
            Sql.update(c, "INSERT INTO afk_milestones (player_uuid, day_key, milestone_min, reached_at) VALUES (?, ?, ?, ?)", player, day, minutes, now);
            return true;
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062 || "23505".equals(e.getSQLState())) {
                return false;
            }
            throw e;
        }
    }

    // ------------------------------------------------------------------ grants (keys, fuel)

    /** Adds to the open grant of this account, profile, kind and target, or opens one. */
    void addGrant(Connection c, UUID player, UUID profileKey, String kind, String ref, long amount, long now) throws SQLException {
        Optional<Long> open = Sql.queryOne(c, "SELECT grant_id FROM afk_grants WHERE player_uuid = ? AND profile_key = ? AND grant_kind = ? "
                + "AND target_ref = ? AND delivered_at IS NULL ORDER BY grant_id LIMIT 1", rs -> rs.getLong(1), player, profileKey, kind, ref);
        if (open.isPresent()) {
            Sql.update(c, "UPDATE afk_grants SET amount = amount + ? WHERE grant_id = ?", amount, open.get());
        } else {
            Sql.update(c, "INSERT INTO afk_grants (player_uuid, profile_key, grant_kind, target_ref, amount, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    player, profileKey, kind, ref, amount, now);
        }
    }

    List<Grant> openGrants(Connection c, UUID player) throws SQLException {
        return Sql.query(c, "SELECT grant_id, player_uuid, profile_key, grant_kind, target_ref, amount FROM afk_grants "
                        + "WHERE player_uuid = ? AND delivered_at IS NULL ORDER BY grant_id",
                rs -> new Grant(rs.getLong("grant_id"), Sql.uuid(rs, "player_uuid"), Sql.uuid(rs, "profile_key"), rs.getString("grant_kind"),
                        rs.getString("target_ref"), rs.getLong("amount")), player);
    }

    /** Claims a grant before it is handed out (a crash after this can lose it but never give it twice). */
    boolean claim(Connection c, long grantId, long now) throws SQLException {
        return Sql.update(c, "UPDATE afk_grants SET delivered_at = ? WHERE grant_id = ? AND delivered_at IS NULL", now, grantId) == 1;
    }

    /** Gives a claimed grant back when the hand-out failed (it is merged into the open one if there is one). */
    void unclaim(Connection c, Grant g, long now) throws SQLException {
        Sql.update(c, "DELETE FROM afk_grants WHERE grant_id = ?", g.id());
        addGrant(c, g.player(), g.profileKey(), g.kind(), g.ref(), g.amount(), now);
    }

    // ------------------------------------------------------------------ boosters

    Booster booster(Connection c, UUID profileKey) throws SQLException {
        return Sql.queryOne(c, "SELECT bonus_percent, banked_ms, active_until FROM afk_boosters WHERE profile_key = ?",
                rs -> new Booster(rs.getInt("bonus_percent"), rs.getLong("banked_ms"), rs.getLong("active_until")), profileKey).orElse(Booster.NONE);
    }

    /** Banks booster time (capped at {@code maxMillis}); the percent of the newest win applies. */
    Booster bank(Connection c, UUID profileKey, int percent, long addMillis, long maxMillis, long now) throws SQLException {
        Booster cur = booster(c, profileKey);
        long banked = Math.min(maxMillis, cur.bankedMillis() + addMillis);
        if (Sql.update(c, "UPDATE afk_boosters SET bonus_percent = ?, banked_ms = ?, updated_at = ? WHERE profile_key = ?",
                percent, banked, now, profileKey) == 0) {
            Sql.update(c, "INSERT INTO afk_boosters (profile_key, bonus_percent, banked_ms, active_until, updated_at) VALUES (?, ?, ?, 0, ?)",
                    profileKey, percent, banked, now);
        }
        return new Booster(percent, banked, cur.activeUntil());
    }

    /** Starts the banked time (added to a running booster). Returns the new state, or empty when nothing was banked. */
    Optional<Booster> activate(Connection c, UUID profileKey, long now) throws SQLException {
        Booster cur = booster(c, profileKey);
        if (cur.bankedMillis() <= 0) {
            return Optional.empty();
        }
        long until = Math.max(now, cur.activeUntil()) + cur.bankedMillis();
        int changed = Sql.update(c, "UPDATE afk_boosters SET banked_ms = 0, active_until = ?, updated_at = ? WHERE profile_key = ? AND banked_ms = ?",
                until, now, profileKey, cur.bankedMillis());
        return changed == 1 ? Optional.of(new Booster(cur.percent(), 0, until)) : Optional.empty();
    }
}

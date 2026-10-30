package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQL of the rewards module (no Bukkit; tested on H2 in MySQL mode). Every method uses only the connection it gets. */
final class RewardStore {

    static final String PENDING = "pending";
    static final String SENDING = "sending";
    static final String DELIVERED = "delivered";
    static final String FAILED = "failed";
    static final String REVIEW = "review";

    /** One hand-out row ({@code reward_grants}). */
    record GrantRow(UUID id, UUID profile, UUID player, String source, String sourceRef, String kind, String ref, long amount,
                    String state, long createdAt) {
    }

    private RewardStore() {
    }

    // ================================================================== daily calendar

    static Optional<DailyRules.State> calendar(Connection c, UUID profile) throws SQLException {
        return Sql.queryOne(c, "SELECT calendar_day, last_claim_date, streak_days, best_streak, lost_streak, streak_broken_on, "
                        + "total_claims FROM daily_calendar WHERE profile_key = ?",
                rs -> new DailyRules.State(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6),
                        rs.getInt(7)), profile);
    }

    /**
     * Writes the state after a claim, only if the stored state is still {@code before} (empty = no row yet). False when
     * it changed meanwhile (another claim): nothing was written.
     */
    static boolean saveCalendar(Connection c, UUID profile, Optional<DailyRules.State> before, DailyRules.State after, long now)
            throws SQLException {
        if (before.isEmpty()) {
            if (calendar(c, profile).isPresent()) {
                return false;
            }
            Sql.update(c, "INSERT INTO daily_calendar (profile_key, calendar_day, last_claim_date, streak_days, best_streak, "
                            + "lost_streak, streak_broken_on, total_claims, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    profile, after.calendarDay(), after.lastClaimDate(), after.streak(), after.best(), after.lostStreak(),
                    after.brokenOn(), after.total(), now);
            return true;
        }
        DailyRules.State b = before.get();
        return Sql.update(c, "UPDATE daily_calendar SET calendar_day = ?, last_claim_date = ?, streak_days = ?, best_streak = ?, "
                        + "lost_streak = ?, streak_broken_on = ?, total_claims = ?, updated_at = ? WHERE profile_key = ? "
                        + "AND calendar_day = ? AND last_claim_date = ? AND streak_days = ? AND total_claims = ?",
                after.calendarDay(), after.lastClaimDate(), after.streak(), after.best(), after.lostStreak(), after.brokenOn(),
                after.total(), now, profile, b.calendarDay(), b.lastClaimDate(), b.streak(), b.total()) == 1;
    }

    /** The claim row; its primary key (profile, date) makes a second claim on the same day fail. */
    static void insertClaim(Connection c, UUID profile, int date, int day, int streakAfter, UUID player, long now) throws SQLException {
        Sql.update(c, "INSERT INTO daily_claims (profile_key, claim_date, calendar_day, streak_after, player_uuid, claimed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", profile, date, day, streakAfter, player, now);
    }

    static int claimCount(Connection c, UUID profile) throws SQLException {
        return Sql.queryOne(c, "SELECT COUNT(*) FROM daily_claims WHERE profile_key = ?", rs -> rs.getInt(1), profile).orElse(0);
    }

    // ================================================================== playtime

    static PlaytimeRules.Row playtime(Connection c, UUID profile) throws SQLException {
        return Sql.queryOne(c, "SELECT active_seconds, hours_paid, tens_paid, title_hours FROM playtime_rewards WHERE profile_key = ?",
                rs -> new PlaytimeRules.Row(rs.getLong(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)), profile).orElse(PlaytimeRules.Row.NONE);
    }

    /** Adds active seconds (relative, so it never undoes a payout written meanwhile) and returns the row after. */
    static PlaytimeRules.Row addSeconds(Connection c, UUID profile, long seconds, long now) throws SQLException {
        if (seconds > 0) {
            int n = Sql.update(c, "UPDATE playtime_rewards SET active_seconds = active_seconds + ?, updated_at = ? WHERE profile_key = ?",
                    seconds, now, profile);
            if (n == 0) {
                Sql.update(c, "INSERT INTO playtime_rewards (profile_key, active_seconds, hours_paid, tens_paid, title_hours, updated_at) "
                        + "VALUES (?, ?, 0, 0, 0, ?)", profile, seconds, now);
            }
        }
        return playtime(c, profile);
    }

    /** Marks milestones paid, only if they are still what {@code before} says (false = changed, nothing written). */
    static boolean payMilestones(Connection c, UUID profile, PlaytimeRules.Row before, PlaytimeRules.Row after, long now)
            throws SQLException {
        return Sql.update(c, "UPDATE playtime_rewards SET hours_paid = ?, tens_paid = ?, title_hours = ?, updated_at = ? "
                        + "WHERE profile_key = ? AND hours_paid = ? AND tens_paid = ? AND title_hours = ?",
                after.hoursPaid(), after.tensPaid(), after.titleHours(), now, profile, before.hoursPaid(), before.tensPaid(),
                before.titleHours()) == 1;
    }

    // ================================================================== grants

    static void insertGrants(Connection c, List<GrantRow> rows) throws SQLException {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> params = new ArrayList<>(rows.size());
        for (GrantRow r : rows) {
            params.add(new Object[]{r.id(), r.profile(), r.player(), r.source(), r.sourceRef(), r.kind(), r.ref(), r.amount(), r.state(),
                    r.createdAt(), r.createdAt()});
        }
        Sql.batch(c, "INSERT INTO reward_grants (grant_id, profile_key, player_uuid, grant_source, source_ref, grant_kind, grant_ref, "
                + "amount, grant_state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", params);
    }

    /** Moves one grant from {@code from} to {@code to}; false when it is not in {@code from} anymore (handled elsewhere). */
    static boolean move(Connection c, UUID grantId, String from, String to, long now) throws SQLException {
        return Sql.update(c, "UPDATE reward_grants SET grant_state = ?, updated_at = ? WHERE grant_id = ? AND grant_state = ?",
                to, now, grantId, from) == 1;
    }

    static List<GrantRow> grants(Connection c, String state, int limit) throws SQLException {
        return Sql.query(c, "SELECT grant_id, profile_key, player_uuid, grant_source, source_ref, grant_kind, grant_ref, amount, "
                + "grant_state, created_at FROM reward_grants WHERE grant_state = ? ORDER BY created_at LIMIT " + Math.max(1, limit),
                rs -> new GrantRow(Sql.uuid(rs, "grant_id"), Sql.uuid(rs, "profile_key"), Sql.uuid(rs, "player_uuid"),
                        rs.getString("grant_source"), rs.getString("source_ref"), rs.getString("grant_kind"), rs.getString("grant_ref"),
                        rs.getLong("amount"), rs.getString("grant_state"), rs.getLong("created_at")), state);
    }

    static Optional<String> grantState(Connection c, UUID grantId) throws SQLException {
        return Sql.queryOne(c, "SELECT grant_state FROM reward_grants WHERE grant_id = ?", rs -> rs.getString(1), grantId);
    }

    /** Drops delivered grants older than {@code before} (history is in the audit log). */
    static int pruneDelivered(Connection c, long before) throws SQLException {
        return Sql.update(c, "DELETE FROM reward_grants WHERE grant_state = ? AND updated_at < ?", DELIVERED, before);
    }

    // ================================================================== profiles

    /** A deleted profile loses its calendar, playtime and open grants (P4: nothing moves to another profile). */
    static void deleteProfile(Connection c, UUID profile) throws SQLException {
        Sql.update(c, "DELETE FROM daily_calendar WHERE profile_key = ?", profile);
        Sql.update(c, "DELETE FROM daily_claims WHERE profile_key = ?", profile);
        Sql.update(c, "DELETE FROM playtime_rewards WHERE profile_key = ?", profile);
        Sql.update(c, "DELETE FROM reward_grants WHERE profile_key = ? AND grant_state IN (?, ?)", profile, PENDING, DELIVERED);
    }
}

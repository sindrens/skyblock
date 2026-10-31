package net.skybornmc.skyblock.pass;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongToIntFunction;

/** SQL of the battle pass (no Bukkit; tested on H2 in MySQL mode). Every method uses only the connection it gets. */
final class PassStore {

    static final String PENDING = "pending";
    static final String SENDING = "sending";
    static final String DELIVERED = "delivered";
    static final String REVIEW = "review";
    static final String FAILED = "failed";

    static final String KEY = "key";
    static final String BOOSTER = "booster";
    static final String TITLE = "title";
    static final String TAG = "tag";

    /** One hand-out row ({@code pass_grants}). */
    record GrantRow(UUID id, UUID profile, UUID player, String season, int tier, String kind, String ref, long amount, String state,
                    long createdAt) {
    }

    /**
     * What an XP add did.
     *
     * @param duplicate true when the event key was stored already: nothing was added (a retry of a committed add)
     */
    record XpResult(long before, long after, int added, boolean duplicate) {
    }

    private PassStore() {
    }

    // ================================================================== XP

    static long xp(Connection c, UUID profile, String season) throws SQLException {
        return Sql.queryOne(c, "SELECT pass_xp FROM pass_progress WHERE profile_key = ? AND season_id = ?", rs -> rs.getLong(1),
                profile, season).orElse(0L);
    }

    /** The XP row, locked until the transaction ends (claims and adds of one profile run one after the other). */
    static long lockXp(Connection c, UUID profile, String season) throws SQLException {
        return Sql.queryOne(c, "SELECT pass_xp FROM pass_progress WHERE profile_key = ? AND season_id = ? FOR UPDATE",
                rs -> rs.getLong(1), profile, season).orElse(0L);
    }

    /**
     * Adds XP once per event key (run in a transaction). {@code amount} gets the XP before the add and returns what to add
     * (the catch-up multiplier depends on the level before). A known event key adds nothing and reports a duplicate.
     */
    static XpResult addXp(Connection c, UUID profile, String season, String eventKey, String source, LongToIntFunction amount, long now)
            throws SQLException {
        if (eventKey != null && Sql.queryOne(c, "SELECT xp_added FROM pass_xp_events WHERE event_key = ?", rs -> rs.getInt(1), eventKey)
                .isPresent()) {
            long xp = xp(c, profile, season);
            return new XpResult(xp, xp, 0, true);
        }
        Optional<Long> row = Sql.queryOne(c, "SELECT pass_xp FROM pass_progress WHERE profile_key = ? AND season_id = ? FOR UPDATE",
                rs -> rs.getLong(1), profile, season);
        long before = row.orElse(0L);
        int add = Math.max(0, amount.applyAsInt(before));
        long after = before + add;
        if (row.isPresent()) {
            Sql.update(c, "UPDATE pass_progress SET pass_xp = ?, updated_at = ? WHERE profile_key = ? AND season_id = ?", after, now,
                    profile, season);
        } else {
            Sql.update(c, "INSERT INTO pass_progress (profile_key, season_id, pass_xp, updated_at) VALUES (?, ?, ?, ?)", profile, season,
                    after, now);
        }
        if (eventKey != null) {
            Sql.update(c, "INSERT INTO pass_xp_events (event_key, profile_key, season_id, xp_added, xp_source, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)", eventKey, profile, season, add, cut(source, 32), now);
        }
        return new XpResult(before, after, add, false);
    }

    /** Sets a profile's XP (staff reset). Claimed tiers stay claimed, so nothing can be claimed twice. */
    static void setXp(Connection c, UUID profile, String season, long xp, long now) throws SQLException {
        if (Sql.update(c, "UPDATE pass_progress SET pass_xp = ?, updated_at = ? WHERE profile_key = ? AND season_id = ?", xp, now, profile,
                season) == 0) {
            Sql.update(c, "INSERT INTO pass_progress (profile_key, season_id, pass_xp, updated_at) VALUES (?, ?, ?, ?)", profile, season,
                    xp, now);
        }
    }

    // ================================================================== claims

    static Set<Integer> claimed(Connection c, UUID owner, String season, PassTable.Track track) throws SQLException {
        return new HashSet<>(Sql.query(c, "SELECT tier_level FROM pass_claims WHERE owner_key = ? AND season_id = ? AND pass_track = ?",
                rs -> rs.getInt(1), owner, season, track.id()));
    }

    /**
     * Writes claim rows; false (nothing should commit) when one of the tiers was claimed already. The primary key also stops
     * a second transaction that races past the check.
     */
    static boolean claim(Connection c, UUID owner, String season, PassTable.Track track, List<Integer> tiers, UUID profile, UUID player,
                         long now) throws SQLException {
        for (int tier : tiers) {
            if (Sql.queryOne(c, "SELECT claimed_at FROM pass_claims WHERE owner_key = ? AND season_id = ? AND pass_track = ? "
                    + "AND tier_level = ?", rs -> rs.getLong(1), owner, season, track.id(), tier).isPresent()) {
                return false;
            }
        }
        List<Object[]> rows = new ArrayList<>(tiers.size());
        for (int tier : tiers) {
            rows.add(new Object[]{owner, season, track.id(), tier, profile, player, now});
        }
        if (!rows.isEmpty()) {
            Sql.batch(c, "INSERT INTO pass_claims (owner_key, season_id, pass_track, tier_level, profile_key, player_uuid, claimed_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)", rows);
        }
        return true;
    }

    // ================================================================== premium

    static boolean premium(Connection c, UUID player, String season) throws SQLException {
        return Sql.queryOne(c, "SELECT granted_at FROM pass_premium WHERE player_uuid = ? AND season_id = ?", rs -> rs.getLong(1),
                player, season).isPresent();
    }

    /** Grants or removes premium for an account and season; true when something changed. */
    static boolean setPremium(Connection c, UUID player, String season, boolean on, String by, long now) throws SQLException {
        if (!on) {
            return Sql.update(c, "DELETE FROM pass_premium WHERE player_uuid = ? AND season_id = ?", player, season) > 0;
        }
        if (premium(c, player, season)) {
            return false;
        }
        Sql.update(c, "INSERT INTO pass_premium (player_uuid, season_id, granted_by, granted_at) VALUES (?, ?, ?, ?)", player, season,
                cut(by, 48), now);
        return true;
    }

    // ================================================================== grants

    static void insertGrants(Connection c, List<GrantRow> rows) throws SQLException {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> params = new ArrayList<>(rows.size());
        for (GrantRow r : rows) {
            params.add(new Object[]{r.id(), r.profile(), r.player(), r.season(), r.tier(), r.kind(), cut(r.ref(), 64), r.amount(), r.state(),
                    r.createdAt(), r.createdAt()});
        }
        Sql.batch(c, "INSERT INTO pass_grants (grant_id, profile_key, player_uuid, season_id, tier_level, grant_kind, grant_ref, amount, "
                + "grant_state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", params);
    }

    static boolean move(Connection c, UUID grantId, String from, String to, long now) throws SQLException {
        return Sql.update(c, "UPDATE pass_grants SET grant_state = ?, updated_at = ? WHERE grant_id = ? AND grant_state = ?",
                to, now, grantId, from) == 1;
    }

    static List<GrantRow> grants(Connection c, String state, int limit) throws SQLException {
        return Sql.query(c, "SELECT grant_id, profile_key, player_uuid, season_id, tier_level, grant_kind, grant_ref, amount, grant_state, "
                        + "created_at FROM pass_grants WHERE grant_state = ? ORDER BY created_at LIMIT " + Math.max(1, limit),
                rs -> new GrantRow(Sql.uuid(rs, "grant_id"), Sql.uuid(rs, "profile_key"), Sql.uuid(rs, "player_uuid"),
                        rs.getString("season_id"), rs.getInt("tier_level"), rs.getString("grant_kind"), rs.getString("grant_ref"),
                        rs.getLong("amount"), rs.getString("grant_state"), rs.getLong("created_at")), state);
    }

    // ================================================================== housekeeping

    /** Drops XP event keys older than {@code eventsBefore} and delivered hand-outs older than {@code grantsBefore}. */
    static void prune(Connection c, long eventsBefore, long grantsBefore) throws SQLException {
        Sql.update(c, "DELETE FROM pass_xp_events WHERE created_at < ?", eventsBefore);
        Sql.update(c, "DELETE FROM pass_grants WHERE grant_state = ? AND updated_at < ?", DELIVERED, grantsBefore);
    }

    /** A deleted profile loses its pass progress and free claims (P4). Premium stays with the account; open grants stay. */
    static void deleteProfile(Connection c, UUID profile) throws SQLException {
        Sql.update(c, "DELETE FROM pass_progress WHERE profile_key = ?", profile);
        Sql.update(c, "DELETE FROM pass_claims WHERE owner_key = ? AND pass_track = ?", profile, PassTable.Track.FREE.id());
        Sql.update(c, "DELETE FROM pass_grants WHERE profile_key = ? AND grant_state IN (?, ?)", profile, PENDING, DELIVERED);
    }

    static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}

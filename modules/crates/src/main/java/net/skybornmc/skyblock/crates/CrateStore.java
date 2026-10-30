package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL of the crates module (tables in {@code db/crates/V001__crates.sql}). Static methods on a caller's connection, so
 * an open can run them inside one economy transaction ({@code TxGuard}). Every change of a counter is a conditional
 * {@code UPDATE}: there is no read-modify-write in Java, so two opens can never spend the same key.
 */
final class CrateStore {

    static final String DONE = "done";
    static final String PENDING = "pending";
    static final String DELIVERED = "delivered";
    static final String FAILED = "failed";
    static final String REVIEW = "review";

    /** Keys of one crate on one profile. */
    record Keys(long bound, long tradeable) {

        static final Keys NONE = new Keys(0, 0);

        long total() {
            return bound + tradeable;
        }
    }

    /** A stored pity row (raw: a row of another season counts as 0, see {@link #counter}). */
    record Pity(int opensSinceTop, int season) {

        /** The counter that applies in {@code currentSeason}. */
        int counter(int currentSeason) {
            return season == currentSeason ? Math.max(0, opensSinceTop) : 0;
        }
    }

    /** What an open needs to know before rolling. */
    record State(Keys keys, Optional<Pity> pity) {
    }

    /** One open as stored in {@code crate_opens}. */
    record OpenRow(UUID openId, UUID batchId, UUID profile, UUID player, String crate, String rewardId, String kind, String ref,
                   long amount, boolean top, boolean forced, boolean boundKey, String state, int season, long openedAt) {
    }

    private CrateStore() {
    }

    // ------------------------------------------------------------------ keys

    static Map<String, Keys> keys(Connection c, UUID profile) throws SQLException {
        Map<String, Keys> out = new LinkedHashMap<>();
        for (Object[] row : Sql.query(c, "SELECT crate_id, bound_amount, tradeable_amount FROM crate_keys WHERE profile_key = ?",
                rs -> new Object[]{rs.getString(1), new Keys(rs.getLong(2), rs.getLong(3))}, profile)) {
            out.put((String) row[0], (Keys) row[1]);
        }
        return out;
    }

    static Keys keys(Connection c, UUID profile, String crate) throws SQLException {
        return Sql.queryOne(c, "SELECT bound_amount, tradeable_amount FROM crate_keys WHERE profile_key = ? AND crate_id = ?",
                rs -> new Keys(rs.getLong(1), rs.getLong(2)), profile, crate).orElse(Keys.NONE);
    }

    /** Adds keys (both counters ≥ 0). */
    static void addKeys(Connection c, UUID profile, String crate, long bound, long tradeable, long now) throws SQLException {
        if (bound < 0 || tradeable < 0) {
            throw new SQLException("negative key grant");
        }
        if (bound == 0 && tradeable == 0) {
            return;
        }
        if (Sql.update(c, "UPDATE crate_keys SET bound_amount = bound_amount + ?, tradeable_amount = tradeable_amount + ?, "
                + "updated_at = ? WHERE profile_key = ? AND crate_id = ?", bound, tradeable, now, profile, crate) == 0) {
            Sql.update(c, "INSERT INTO crate_keys (profile_key, crate_id, bound_amount, tradeable_amount, updated_at) VALUES (?, ?, ?, ?, ?)",
                    profile, crate, bound, tradeable, now);
        }
    }

    /** Takes keys only if both counters still hold enough; true if taken. */
    static boolean take(Connection c, UUID profile, String crate, long bound, long tradeable, long now) throws SQLException {
        if (bound < 0 || tradeable < 0 || bound + tradeable == 0) {
            return false;
        }
        return Sql.update(c, "UPDATE crate_keys SET bound_amount = bound_amount - ?, tradeable_amount = tradeable_amount - ?, "
                        + "updated_at = ? WHERE profile_key = ? AND crate_id = ? AND bound_amount >= ? AND tradeable_amount >= ?",
                bound, tradeable, now, profile, crate, bound, tradeable) == 1;
    }

    /** A staff take: what was there before, how many bound / tradeable keys were taken, and whether it happened. */
    record StaffTake(Keys before, long bound, long tradeable, boolean done) {

        long total() {
            return bound + tradeable;
        }
    }

    /**
     * Staff take ({@code /crates admin take}): {@code amount} keys, bound first, all or nothing. Not {@code done} when
     * the profile has fewer keys ({@code before} is then what it has and nothing changed). The conditional
     * {@code UPDATE} is retried when an open or a grant changed the counters in between, so the result is always exact.
     * Call inside one transaction.
     */
    static StaffTake staffTake(Connection c, UUID profile, String crate, long amount, long now, int attempts) throws SQLException {
        for (int i = 0; i < Math.max(1, attempts); i++) {
            Keys have = keys(c, profile, crate);
            CrateMath.KeyUse use = CrateMath.staffTake(have.bound(), have.tradeable(), amount);
            if (use == null) {
                return new StaffTake(have, 0, 0, false);
            }
            if (take(c, profile, crate, use.bound(), use.tradeable(), now)) {
                return new StaffTake(have, use.bound(), use.tradeable(), true);
            }
        }
        throw new SQLException("crate keys of " + profile + " kept changing during a staff take");
    }

    // ------------------------------------------------------------------ pity

    static Optional<Pity> pity(Connection c, UUID profile, String crate) throws SQLException {
        return Sql.queryOne(c, "SELECT opens_since_top, season_id FROM crate_pity WHERE profile_key = ? AND crate_id = ?",
                rs -> new Pity(rs.getInt(1), rs.getInt(2)), profile, crate);
    }

    static Map<String, Pity> pities(Connection c, UUID profile) throws SQLException {
        Map<String, Pity> out = new LinkedHashMap<>();
        for (Object[] row : Sql.query(c, "SELECT crate_id, opens_since_top, season_id FROM crate_pity WHERE profile_key = ?",
                rs -> new Object[]{rs.getString(1), new Pity(rs.getInt(2), rs.getInt(3))}, profile)) {
            out.put((String) row[0], (Pity) row[1]);
        }
        return out;
    }

    /**
     * Sets the counter, but only if the row is still exactly {@code before} (empty = there was no row): another open in
     * between makes this return false and the whole open rolls back.
     */
    static boolean setPity(Connection c, UUID profile, String crate, Optional<Pity> before, int counter, int season, long now)
            throws SQLException {
        if (before.isPresent()) {
            return Sql.update(c, "UPDATE crate_pity SET opens_since_top = ?, season_id = ?, updated_at = ? WHERE profile_key = ? "
                            + "AND crate_id = ? AND opens_since_top = ? AND season_id = ?", counter, season, now, profile, crate,
                    before.get().opensSinceTop(), before.get().season()) == 1;
        }
        if (pity(c, profile, crate).isPresent()) {
            return false; // another open wrote the first row meanwhile
        }
        return Sql.update(c, "INSERT INTO crate_pity (profile_key, crate_id, opens_since_top, season_id, updated_at) VALUES (?, ?, ?, ?, ?)",
                profile, crate, counter, season, now) == 1;
    }

    static State state(Connection c, UUID profile, String crate) throws SQLException {
        return new State(keys(c, profile, crate), pity(c, profile, crate));
    }

    // ------------------------------------------------------------------ opens

    static void insertOpens(Connection c, List<OpenRow> rows) throws SQLException {
        List<Object[]> params = new ArrayList<>(rows.size());
        for (OpenRow r : rows) {
            params.add(new Object[]{r.openId(), r.batchId(), r.profile(), r.player(), r.crate(), r.rewardId(), r.kind(), r.ref(),
                    r.amount(), r.top(), r.forced(), r.boundKey(), r.state(), r.season(), r.openedAt(), r.openedAt()});
        }
        Sql.batch(c, "INSERT INTO crate_opens (open_id, batch_id, profile_key, player_uuid, crate_id, reward_id, reward_kind, "
                + "reward_ref, reward_amount, top_band, pity_forced, bound_key, grant_state, season_id, opened_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", params);
    }

    /** {@code from → to} for one open; true if this call changed it (exactly once). */
    static boolean move(Connection c, UUID openId, String from, String to, long now) throws SQLException {
        return Sql.update(c, "UPDATE crate_opens SET grant_state = ?, updated_at = ? WHERE open_id = ? AND grant_state = ?",
                to, now, openId, from) == 1;
    }

    static List<OpenRow> byState(Connection c, String state) throws SQLException {
        return Sql.query(c, "SELECT * FROM crate_opens WHERE grant_state = ? ORDER BY opened_at", CrateStore::map, state);
    }

    static List<OpenRow> batch(Connection c, UUID batchId) throws SQLException {
        return Sql.query(c, "SELECT * FROM crate_opens WHERE batch_id = ? ORDER BY opened_at", CrateStore::map, batchId);
    }

    /**
     * Startup: opens left {@code pending} by a crash or stop. Cosmetic grants are idempotent and come back to be granted
     * again (they stay pending); items and fuel may already have been handed over, so they become {@code review} for
     * staff and are never handed out twice. Returns {cosmetics to grant again, rows moved to review}.
     */
    static List<List<OpenRow>> recover(Connection c, long now) throws SQLException {
        List<OpenRow> again = new ArrayList<>();
        List<OpenRow> review = new ArrayList<>();
        for (OpenRow r : byState(c, PENDING)) {
            if (r.kind().equals(RewardType.TAG.key()) || r.kind().equals(RewardType.TITLE.key())) {
                again.add(r);
            } else if (move(c, r.openId(), PENDING, REVIEW, now)) {
                review.add(r);
            }
        }
        return List.of(again, review);
    }

    /** A deleted profile: its keys and pity go (the open history stays as a log). */
    static void deleteProfile(Connection c, UUID profile) throws SQLException {
        Sql.update(c, "DELETE FROM crate_keys WHERE profile_key = ?", profile);
        Sql.update(c, "DELETE FROM crate_pity WHERE profile_key = ?", profile);
    }

    static OpenRow map(ResultSet rs) throws SQLException {
        return new OpenRow(Sql.uuid(rs, "open_id"), Sql.uuid(rs, "batch_id"), Sql.uuid(rs, "profile_key"), Sql.uuid(rs, "player_uuid"),
                rs.getString("crate_id"), rs.getString("reward_id"), rs.getString("reward_kind"), rs.getString("reward_ref"),
                rs.getLong("reward_amount"), rs.getBoolean("top_band"), rs.getBoolean("pity_forced"), rs.getBoolean("bound_key"),
                rs.getString("grant_state"), rs.getInt("season_id"), rs.getLong("opened_at"));
    }
}

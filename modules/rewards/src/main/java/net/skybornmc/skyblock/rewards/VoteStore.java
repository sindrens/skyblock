package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** SQL of voting (no Bukkit; tested on H2 in MySQL mode). Every method uses only the connection it gets. */
final class VoteStore {

    static final int PARTY_ROW = 1;

    /** One stored vote. */
    record Row(String voteId, String siteId, String service, String username, String nameKey, UUID account, String addressHash,
               int protocol, long siteTime, long receivedAt, int voteDate, String state) {
    }

    /** The party counter row. */
    record Party(int counter, int total) {
        static final Party NONE = new Party(0, 0);
    }

    private static final String COLUMNS = "vote_id, site_id, service_name, username, name_key, account_id, address_hash, protocol, "
            + "site_time, received_at, vote_date, vote_state";

    private VoteStore() {
    }

    // ================================================================== votes

    /** Stores a new vote as queued; false when the same vote id is already stored (a repeated packet). */
    static boolean insert(Connection c, Row r) throws SQLException {
        if (Sql.queryOne(c, "SELECT 1 FROM vote_log WHERE vote_id = ?", rs -> 1, r.voteId()).isPresent()) {
            return false;
        }
        Sql.update(c, "INSERT INTO vote_log (" + COLUMNS + ", paid_profile, paid_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 0)",
                r.voteId(), r.siteId(), r.service(), r.username(), r.nameKey(), r.account(), r.addressHash(), r.protocol(), r.siteTime(),
                r.receivedAt(), r.voteDate(), r.state());
        return true;
    }

    static Optional<Row> vote(Connection c, String voteId) throws SQLException {
        return Sql.queryOne(c, "SELECT " + COLUMNS + " FROM vote_log WHERE vote_id = ?", VoteStore::row, voteId);
    }

    /** Waiting votes of an account, plus unknown-name votes for one of {@code nameKeys}, oldest first. */
    static List<Row> queuedFor(Connection c, UUID account, List<String> nameKeys) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM vote_log WHERE vote_state = ? AND (account_id = ?");
        Object[] params = new Object[2 + nameKeys.size()];
        params[0] = VoteRules.QUEUED;
        params[1] = account;
        if (!nameKeys.isEmpty()) {
            sql.append(" OR (account_id IS NULL AND name_key IN (");
            for (int i = 0; i < nameKeys.size(); i++) {
                sql.append(i == 0 ? "?" : ", ?");
                params[2 + i] = nameKeys.get(i);
            }
            sql.append("))");
        }
        sql.append(") ORDER BY received_at LIMIT 100");
        return Sql.query(c, sql.toString(), VoteStore::row, params);
    }

    /** Number of waiting votes of an account (for the /menu Rewards button). */
    static int queuedCount(Connection c, UUID account, List<String> nameKeys) throws SQLException {
        return queuedFor(c, account, nameKeys).size();
    }

    /** Received times of the account's paid votes on a site within {@code [from, to]}, without {@code exceptId}. */
    static List<Long> paidTimes(Connection c, UUID account, String siteId, long from, long to, String exceptId) throws SQLException {
        return Sql.query(c, "SELECT received_at FROM vote_log WHERE account_id = ? AND site_id = ? AND vote_state = ? "
                        + "AND received_at >= ? AND received_at <= ? AND vote_id <> ?", rs -> rs.getLong(1), account, siteId, VoteRules.PAID,
                from, to, exceptId);
    }

    /** Accounts with a paid vote from this address hash on this day. */
    static Set<UUID> accountsOnAddress(Connection c, String addressHash, int date) throws SQLException {
        if (addressHash.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(Sql.query(c, "SELECT DISTINCT account_id FROM vote_log WHERE address_hash = ? AND vote_date = ? "
                + "AND vote_state = ? AND account_id IS NOT NULL", rs -> Sql.uuid(rs, "account_id"), addressHash, date, VoteRules.PAID));
    }

    /** The newest paid vote per site of an account (site id → received time). */
    static Map<String, Long> lastPaidPerSite(Connection c, UUID account) throws SQLException {
        Map<String, Long> out = new HashMap<>();
        for (Object[] r : Sql.query(c, "SELECT site_id, MAX(received_at) FROM vote_log WHERE account_id = ? AND vote_state = ? "
                + "GROUP BY site_id", rs -> new Object[]{rs.getString(1), rs.getLong(2)}, account, VoteRules.PAID)) {
            out.put((String) r[0], (Long) r[1]);
        }
        return out;
    }

    /** queued → another state (with the account it belongs to); false when it is not queued anymore. */
    static boolean settle(Connection c, String voteId, UUID account, String state, long now) throws SQLException {
        return Sql.update(c, "UPDATE vote_log SET vote_state = ?, account_id = ?, paid_at = ? WHERE vote_id = ? AND vote_state = ?",
                state, account, now, voteId, VoteRules.QUEUED) == 1;
    }

    /** queued → paid to a profile; false when it is not queued anymore. */
    static boolean markPaid(Connection c, String voteId, UUID account, UUID profile, long now) throws SQLException {
        return Sql.update(c, "UPDATE vote_log SET vote_state = ?, account_id = ?, paid_profile = ?, paid_at = ? WHERE vote_id = ? "
                + "AND vote_state = ?", VoteRules.PAID, account, profile, now, voteId, VoteRules.QUEUED) == 1;
    }

    /** Expires waiting votes: unknown names older than {@code unknownBefore}, known accounts older than {@code knownBefore}. */
    static int expire(Connection c, long unknownBefore, long knownBefore, long now) throws SQLException {
        int a = Sql.update(c, "UPDATE vote_log SET vote_state = ?, paid_at = ? WHERE vote_state = ? AND account_id IS NULL AND received_at < ?",
                VoteRules.EXPIRED, now, VoteRules.QUEUED, unknownBefore);
        int b = Sql.update(c, "UPDATE vote_log SET vote_state = ?, paid_at = ? WHERE vote_state = ? AND account_id IS NOT NULL "
                + "AND received_at < ?", VoteRules.EXPIRED, now, VoteRules.QUEUED, knownBefore);
        return a + b;
    }

    /** Drops settled vote rows older than {@code before}. */
    static int prune(Connection c, long before) throws SQLException {
        return Sql.update(c, "DELETE FROM vote_log WHERE vote_state <> ? AND received_at < ?", VoteRules.QUEUED, before);
    }

    // ================================================================== streak

    static Optional<VoteRules.Streak> streak(Connection c, UUID account) throws SQLException {
        return Sql.queryOne(c, "SELECT streak_days, last_vote_date, best_streak FROM vote_streaks WHERE account_id = ?",
                rs -> new VoteRules.Streak(rs.getInt(1), rs.getInt(2), rs.getInt(3)), account);
    }

    /** Writes the streak only if the stored one is still {@code before} (empty = no row yet). False = changed meanwhile. */
    static boolean saveStreak(Connection c, UUID account, Optional<VoteRules.Streak> before, VoteRules.Streak after, long now)
            throws SQLException {
        if (before.isEmpty()) {
            if (streak(c, account).isPresent()) {
                return false;
            }
            Sql.update(c, "INSERT INTO vote_streaks (account_id, streak_days, last_vote_date, best_streak, updated_at) VALUES (?, ?, ?, ?, ?)",
                    account, after.days(), after.lastDate(), after.best(), now);
            return true;
        }
        VoteRules.Streak b = before.get();
        if (b.equals(after)) {
            return streak(c, account).filter(b::equals).isPresent();
        }
        return Sql.update(c, "UPDATE vote_streaks SET streak_days = ?, last_vote_date = ?, best_streak = ?, updated_at = ? "
                        + "WHERE account_id = ? AND streak_days = ? AND last_vote_date = ? AND best_streak = ?",
                after.days(), after.lastDate(), after.best(), now, account, b.days(), b.lastDate(), b.best()) == 1;
    }

    // ================================================================== party

    static Party party(Connection c) throws SQLException {
        return Sql.queryOne(c, "SELECT vote_count, parties_total FROM vote_party WHERE party_key = ?",
                rs -> new Party(rs.getInt(1), rs.getInt(2)), PARTY_ROW).orElse(Party.NONE);
    }

    /**
     * One more vote on the party counter (the row is locked by the update, so parallel votes count one after another).
     *
     * @return the party number that just filled, or 0
     */
    static int addPartyVote(Connection c, int size, long now) throws SQLException {
        int n = Sql.update(c, "UPDATE vote_party SET vote_count = vote_count + 1, updated_at = ? WHERE party_key = ?", now, PARTY_ROW);
        if (n == 0) {
            Sql.update(c, "INSERT INTO vote_party (party_key, vote_count, parties_total, updated_at) VALUES (?, 1, 0, ?)", PARTY_ROW, now);
        }
        Party p = party(c);
        VoteRules.PartyStep step = VoteRules.addVote(p.counter() - 1, size);
        if (!step.filled()) {
            return 0;
        }
        int number = p.total() + 1;
        Sql.update(c, "UPDATE vote_party SET vote_count = ?, parties_total = ?, updated_at = ? WHERE party_key = ?", step.counter(), number,
                now, PARTY_ROW);
        Sql.update(c, "INSERT INTO vote_parties (party_no, filled_at, party_state, rewarded) VALUES (?, ?, ?, 0)", number, now, "filled");
        return number;
    }

    /** Records one party reward; false when this account already got this party's reward. */
    static boolean insertPartyReward(Connection c, int party, UUID account, UUID profile, long now) throws SQLException {
        if (Sql.queryOne(c, "SELECT 1 FROM vote_party_rewards WHERE party_no = ? AND account_id = ?", rs -> 1, party, account).isPresent()) {
            return false;
        }
        Sql.update(c, "INSERT INTO vote_party_rewards (party_no, account_id, profile_key, paid_at) VALUES (?, ?, ?, ?)", party, account,
                profile, now);
        return true;
    }

    static void partyDone(Connection c, int party, int rewarded) throws SQLException {
        Sql.update(c, "UPDATE vote_parties SET party_state = ?, rewarded = ? WHERE party_no = ?", "paid", rewarded, party);
    }

    /** Parties a stop interrupted (still 'filled'): marked 'review' and returned. */
    static List<Integer> reviewInterrupted(Connection c) throws SQLException {
        List<Integer> open = Sql.query(c, "SELECT party_no FROM vote_parties WHERE party_state = ?", rs -> rs.getInt(1), "filled");
        for (int n : open) {
            Sql.update(c, "UPDATE vote_parties SET party_state = ? WHERE party_no = ? AND party_state = ?", "review", n, "filled");
        }
        return open;
    }

    private static Row row(java.sql.ResultSet rs) throws SQLException {
        return new Row(rs.getString("vote_id"), rs.getString("site_id"), rs.getString("service_name"), rs.getString("username"),
                rs.getString("name_key"), Sql.uuid(rs, "account_id"), rs.getString("address_hash"), rs.getInt("protocol"),
                rs.getLong("site_time"), rs.getLong("received_at"), rs.getInt("vote_date"), rs.getString("vote_state"));
    }
}

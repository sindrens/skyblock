package net.skybornmc.skyblock.economy.bank;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.AccountType;
import net.skybornmc.skyblock.common.economy.Currency;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/**
 * Bank SQL (M3.3): the interest state per bank account, island withdraw counters, activity minutes and the bank history.
 * Balances themselves are economy accounts and only change through ledger transactions ({@code EconomyService}); the
 * statements here that run inside such a transaction are called from its guard with the same connection.
 */
final class BankStore {

    private static final String STATE_COLUMNS = "bank_tier, cycle_no, min_balance, forfeited, settled_cycle";

    private final Database db;
    private final LongSupplier clock;
    /** The period length every state row is written in (rows of another length are converted at start). */
    private final long periodMillis;

    BankStore(Database db, LongSupplier clock, long periodMillis) {
        if (periodMillis < 60_000L || periodMillis % 60_000L != 0) {
            throw new IllegalArgumentException("period must be whole minutes: " + periodMillis);
        }
        this.db = db;
        this.clock = clock;
        this.periodMillis = periodMillis;
    }

    Database db() {
        return db;
    }

    long periodMillis() {
        return periodMillis;
    }

    int periodMinutes() {
        return (int) (periodMillis / 60_000L);
    }

    // ------------------------------------------------------------------------------------------------ state

    private static InterestMath.State state(java.sql.ResultSet rs) throws SQLException {
        return new InterestMath.State(rs.getInt("bank_tier"), rs.getLong("cycle_no"), rs.getLong("min_balance"),
                rs.getBoolean("forfeited"), rs.getLong("settled_cycle"));
    }

    static Optional<InterestMath.State> loadState(Connection c, AccountRef account) throws SQLException {
        return Sql.queryOne(c, "SELECT " + STATE_COLUMNS + " FROM eco_bank_state WHERE owner_type = ? AND owner_id = ?",
                BankStore::state, account.type().key(), account.owner());
    }

    /** Locks the state row (inside a transaction). Empty if missing. */
    static Optional<InterestMath.State> lockState(Connection c, AccountRef account) throws SQLException {
        return Sql.queryOne(c, "SELECT " + STATE_COLUMNS + " FROM eco_bank_state WHERE owner_type = ? AND owner_id = ? FOR UPDATE",
                BankStore::state, account.type().key(), account.owner());
    }

    CompletableFuture<Optional<InterestMath.State>> state(AccountRef account) {
        return db.async(c -> loadState(c, account));
    }

    /** The account's money balance (0 without a row). */
    static long balance(Connection c, AccountRef account) throws SQLException {
        return Sql.queryOne(c, "SELECT balance FROM eco_accounts WHERE owner_type = ? AND owner_id = ? AND currency = ?",
                rs -> rs.getLong(1), account.type().key(), account.owner(), Currency.MONEY.key()).orElse(0L);
    }

    /**
     * Creates the state row if missing (autocommit, before a transaction: avoids gap-lock deadlocks): first used during
     * {@code period}, lowest balance = the balance now, nothing due for earlier periods.
     */
    void ensureState(Connection c, AccountRef account, long period) throws SQLException {
        if (loadState(c, account).isPresent()) {
            return;
        }
        InterestMath.State fresh = InterestMath.State.fresh(1, period, balance(c, account));
        try {
            Sql.update(c, "INSERT INTO eco_bank_state (owner_type, owner_id, bank_tier, cycle_no, min_balance, forfeited, settled_cycle, "
                            + "updated_at, period_minutes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", account.type().key(), account.owner(),
                    fresh.tier(), fresh.cycle(), fresh.minBalance(), fresh.forfeited(), fresh.settledCycle(), clock.getAsLong(),
                    periodMinutes());
        } catch (SQLException e) {
            if (!(e instanceof java.sql.SQLIntegrityConstraintViolationException)
                    && (e.getSQLState() == null || !e.getSQLState().startsWith("23"))) {
                throw e;
            }
            // created at the same time by another request: fine
        }
    }

    CompletableFuture<Void> ensureState(AccountRef account, long period) {
        return db.run(c -> ensureState(c, account, period));
    }

    /** Writes the period fields of a locked state row (inside the transaction). */
    void saveCycle(Connection c, AccountRef account, InterestMath.State s) throws SQLException {
        Sql.update(c, "UPDATE eco_bank_state SET cycle_no = ?, min_balance = ?, forfeited = ?, updated_at = ? "
                + "WHERE owner_type = ? AND owner_id = ?", s.cycle(), s.minBalance(), s.forfeited(), clock.getAsLong(),
                account.type().key(), account.owner());
    }

    /**
     * Marks {@code period} settled if the row is still exactly as read (settled = period - 1, same cycle): the
     * compare-and-set that makes concurrent settlements safe. Returns false if someone else changed it first.
     */
    boolean markSettled(Connection c, AccountRef account, long period, long expectedCycle) throws SQLException {
        return Sql.update(c, "UPDATE eco_bank_state SET settled_cycle = ?, updated_at = ? WHERE owner_type = ? AND owner_id = ? "
                        + "AND settled_cycle = ? AND cycle_no = ?", period, clock.getAsLong(), account.type().key(), account.owner(),
                period - 1, expectedCycle) == 1;
    }

    /** Skips periods without payout (downtime catch-up limit); compare-and-set on the settled period. */
    CompletableFuture<Boolean> skipTo(AccountRef account, long expectedSettled, long newSettled) {
        return db.async(c -> Sql.update(c, "UPDATE eco_bank_state SET settled_cycle = ?, updated_at = ? WHERE owner_type = ? "
                        + "AND owner_id = ? AND settled_cycle = ?", newSettled, clock.getAsLong(), account.type().key(), account.owner(),
                expectedSettled) == 1);
    }

    CompletableFuture<Boolean> markSettledAsync(AccountRef account, long period, long expectedCycle) {
        return db.async(c -> markSettled(c, account, period, expectedCycle));
    }

    /** Raises a personal tier by one (compare-and-set; inside the tier purchase transaction). */
    boolean raiseTier(Connection c, AccountRef account, int expected) throws SQLException {
        return Sql.update(c, "UPDATE eco_bank_state SET bank_tier = ?, updated_at = ? WHERE owner_type = ? AND owner_id = ? "
                + "AND bank_tier = ?", expected + 1, clock.getAsLong(), account.type().key(), account.owner(), expected) == 1;
    }

    CompletableFuture<Void> deleteState(AccountRef account) {
        return db.run(c -> Sql.update(c, "DELETE FROM eco_bank_state WHERE owner_type = ? AND owner_id = ?", account.type().key(),
                account.owner()));
    }

    /** Accounts with a period still to settle, oldest first (background sweep). */
    CompletableFuture<List<AccountRef>> due(long lastDuePeriod, int limit) {
        return db.async(c -> Sql.query(c, "SELECT owner_type, owner_id FROM eco_bank_state WHERE settled_cycle < ? "
                        + "ORDER BY settled_cycle LIMIT ?",
                rs -> {
                    UUID owner = Sql.uuid(rs, "owner_id");
                    return AccountType.byKey(rs.getString(1)).map(t -> new AccountRef(t, owner)).orElse(null);
                },
                lastDuePeriod, limit)).thenApply(list -> list.stream().filter(java.util.Objects::nonNull).toList());
    }

    /** What a period-length conversion did ({@link #convertPeriods}). */
    record Conversion(int rows, int skipped, Map<Integer, Integer> fromMinutes) {
    }

    /**
     * BANK2: converts every state row written with another period length (M3.3 rows: 3 h) to this store's length with
     * {@link InterestMath#convertPeriod}, in batches; each row is compared-and-set on its old values, so a row changed
     * at the same moment is simply left for the next run. Runs once at start before the bank opens.
     */
    CompletableFuture<Conversion> convertPeriods(int batch) {
        int minutes = periodMinutes();
        return db.async(c -> {
            int rows = 0;
            int skipped = 0;
            Map<Integer, Integer> from = new java.util.TreeMap<>();
            while (true) {
                record Row(String type, UUID owner, int minutes, InterestMath.State state) {
                }
                // Converted rows leave the result; rows left alone (skipped) stay in front, so they are stepped over.
                List<Row> page = Sql.query(c, "SELECT owner_type, owner_id, period_minutes, " + STATE_COLUMNS
                                + " FROM eco_bank_state WHERE period_minutes <> ? ORDER BY owner_type, owner_id LIMIT ? OFFSET ?",
                        rs -> new Row(rs.getString("owner_type"), Sql.uuid(rs, "owner_id"), rs.getInt("period_minutes"), state(rs)),
                        minutes, Math.max(1, batch), skipped);
                if (page.isEmpty()) {
                    break;
                }
                for (Row r : page) {
                    if (r.minutes() <= 0) {
                        skipped++;
                        continue;
                    }
                    InterestMath.State s = r.state();
                    InterestMath.State n = InterestMath.convertPeriod(s, r.minutes() * 60_000L, periodMillis);
                    int done = Sql.update(c, "UPDATE eco_bank_state SET cycle_no = ?, settled_cycle = ?, period_minutes = ?, updated_at = ? "
                                    + "WHERE owner_type = ? AND owner_id = ? AND period_minutes = ? AND cycle_no = ? AND settled_cycle = ?",
                            n.cycle(), n.settledCycle(), minutes, clock.getAsLong(), r.type(), r.owner(), r.minutes(), s.cycle(),
                            s.settledCycle());
                    if (done == 1) {
                        rows++;
                        from.merge(r.minutes(), 1, Integer::sum);
                    } else {
                        skipped++;
                    }
                }
            }
            return new Conversion(rows, skipped, from);
        });
    }

    /** Rows still written in another period length (0 after {@link #convertPeriods}). */
    CompletableFuture<Long> unconverted() {
        return db.async(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM eco_bank_state WHERE period_minutes <> ?", rs -> rs.getLong(1),
                periodMinutes()).orElse(0L));
    }

    // ------------------------------------------------------------------------------------------------ withdraw limits

    /**
     * Adds {@code amount} to a member's island withdrawals of {@code day} if the total stays within {@code limit}
     * (inside the withdraw transaction, so a failed withdrawal never counts). Returns false over the limit.
     */
    static boolean addWithdrawn(Connection c, UUID island, UUID member, int day, long amount, long limit) throws SQLException {
        if (limit == Long.MAX_VALUE) {
            return true;
        }
        if (amount > limit) {
            return false;
        }
        Optional<Long> current = Sql.queryOne(c, "SELECT amount FROM eco_bank_withdrawn WHERE island_id = ? AND member_id = ? "
                + "AND day_no = ? FOR UPDATE", rs -> rs.getLong(1), island, member, day);
        if (current.isEmpty()) {
            Sql.update(c, "INSERT INTO eco_bank_withdrawn (island_id, member_id, day_no, amount) VALUES (?, ?, ?, ?)", island, member,
                    day, amount);
            return true;
        }
        if (current.get() > limit - amount) {
            return false;
        }
        Sql.update(c, "UPDATE eco_bank_withdrawn SET amount = amount + ? WHERE island_id = ? AND member_id = ? AND day_no = ?",
                amount, island, member, day);
        return true;
    }

    CompletableFuture<Long> withdrawnOn(UUID island, UUID member, int day) {
        return db.async(c -> Sql.queryOne(c, "SELECT amount FROM eco_bank_withdrawn WHERE island_id = ? AND member_id = ? AND day_no = ?",
                rs -> rs.getLong(1), island, member, day).orElse(0L));
    }

    /** Drops withdraw counters older than {@code beforeDay} (housekeeping). */
    CompletableFuture<Integer> purgeWithdrawn(int beforeDay) {
        return db.async(c -> Sql.update(c, "DELETE FROM eco_bank_withdrawn WHERE day_no < ?", beforeDay));
    }

    // ------------------------------------------------------------------------------------------------ activity

    /** Adds played minutes per profile for {@code day} (one batch). */
    CompletableFuture<Void> addActivity(Map<UUID, Integer> minutesByProfile, int day) {
        if (minutesByProfile.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return db.run(c -> {
            for (Map.Entry<UUID, Integer> e : minutesByProfile.entrySet()) {
                if (e.getValue() <= 0) {
                    continue;
                }
                if (Sql.update(c, "UPDATE eco_bank_activity SET minutes = minutes + ? WHERE profile_id = ? AND day_no = ?",
                        e.getValue(), e.getKey(), day) == 0) {
                    try {
                        Sql.update(c, "INSERT INTO eco_bank_activity (profile_id, day_no, minutes) VALUES (?, ?, ?)", e.getKey(), day,
                                e.getValue());
                    } catch (SQLException duplicate) {
                        Sql.update(c, "UPDATE eco_bank_activity SET minutes = minutes + ? WHERE profile_id = ? AND day_no = ?",
                                e.getValue(), e.getKey(), day);
                    }
                }
            }
        });
    }

    /** The most minutes any of {@code profiles} played since {@code fromDay} (inclusive). */
    CompletableFuture<Integer> bestActivity(Collection<UUID> profiles, int fromDay) {
        if (profiles.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        List<UUID> list = new ArrayList<>(profiles);
        return db.async(c -> {
            int best = 0;
            for (UUID profile : list) {
                int m = Sql.queryOne(c, "SELECT COALESCE(SUM(minutes), 0) FROM eco_bank_activity WHERE profile_id = ? AND day_no >= ?",
                        rs -> rs.getInt(1), profile, fromDay).orElse(0);
                best = Math.max(best, m);
            }
            return best;
        });
    }

    CompletableFuture<Integer> purgeActivity(int beforeDay) {
        return db.async(c -> Sql.update(c, "DELETE FROM eco_bank_activity WHERE day_no < ?", beforeDay));
    }

    // ------------------------------------------------------------------------------------------------ history

    /** One ledger row of a bank account (newest first in {@link #history}). */
    record Entry(long id, long time, boolean incoming, long amount, Long balanceAfter, String reason, String actorName) {
    }

    record Page(List<Entry> rows, long total) {
    }

    CompletableFuture<Page> history(AccountRef account, int offset, int limit) {
        return db.async(c -> {
            Object[] where = {account.type().key(), account.owner(), account.type().key(), account.owner(), Currency.MONEY.key()};
            String cond = " FROM eco_ledger WHERE ((from_type = ? AND from_id = ?) OR (to_type = ? AND to_id = ?)) AND currency = ?";
            long total = Sql.queryOne(c, "SELECT COUNT(*)" + cond, rs -> rs.getLong(1), where).orElse(0L);
            Object[] params = java.util.Arrays.copyOf(where, where.length + 2);
            params[where.length] = Math.max(1, limit);
            params[where.length + 1] = Math.max(0, offset);
            List<Entry> rows = Sql.query(c, "SELECT id, created_at, to_type, to_id, amount, from_balance, to_balance, reason, actor_name"
                    + cond + " ORDER BY id DESC LIMIT ? OFFSET ?", rs -> {
                        boolean in = account.type().key().equals(rs.getString("to_type")) && account.owner().equals(Sql.uuid(rs, "to_id"));
                        return new Entry(rs.getLong("id"), rs.getLong("created_at"), in, rs.getLong("amount"),
                                Sql.nullableLong(rs, in ? "to_balance" : "from_balance"), rs.getString("reason"), rs.getString("actor_name"));
                    }, params);
            return new Page(rows, total);
        });
    }
}

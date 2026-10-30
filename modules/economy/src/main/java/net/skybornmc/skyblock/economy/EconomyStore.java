package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.AccountType;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import net.skybornmc.skyblock.common.profile.BestProfile;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.SlotRules;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * All economy SQL. <b>Balances only change through atomic statements inside one database transaction:</b>
 * <ol>
 *   <li>(autocommit) refuse a known idempotency key; make sure both account rows exist (inserting a missing row outside
 *       the transaction avoids gap-lock deadlocks on MariaDB);</li>
 *   <li>(transaction) lock the account rows with {@code SELECT … FOR UPDATE} in {@link AccountRef#LOCK_ORDER} (no
 *       deadlocks between opposite transfers), {@code UPDATE … SET balance = balance - ? WHERE balance >= ?} for the payer,
 *       {@code UPDATE … SET balance = balance + ? WHERE balance <= MAX - ?} for the receiver, read the balances after,
 *       insert the ledger row (its unique {@code idem_key} stops replays), commit.</li>
 * </ol>
 * Any failed step rolls back everything. Deadlocks/lock timeouts are retried a few times.
 */
final class EconomyStore {

    static final int MAX_ATTEMPTS = 4;

    private static final String LOCK = "SELECT balance FROM eco_accounts WHERE owner_type = ? AND owner_id = ? AND currency = ? FOR UPDATE";
    private static final String READ = "SELECT balance FROM eco_accounts WHERE owner_type = ? AND owner_id = ? AND currency = ?";
    private static final String DEBIT = "UPDATE eco_accounts SET balance = balance - ?, updated_at = ? "
            + "WHERE owner_type = ? AND owner_id = ? AND currency = ? AND balance >= ?";
    private static final String CREDIT = "UPDATE eco_accounts SET balance = balance + ?, updated_at = ? "
            + "WHERE owner_type = ? AND owner_id = ? AND currency = ? AND balance <= ?";
    private static final String COMPARE_AND_SET = "UPDATE eco_accounts SET balance = ?, updated_at = ? "
            + "WHERE owner_type = ? AND owner_id = ? AND currency = ? AND balance = ?";
    private static final String INSERT_LEDGER = "INSERT INTO eco_ledger (created_at, season_id, currency, from_type, from_id, "
            + "to_type, to_id, amount, from_balance, to_balance, reason, reference, idem_key, actor_uuid, actor_name) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String LEDGER_COLUMNS = "l.id, l.created_at, l.season_id, l.currency, l.from_type, l.from_id, l.to_type, "
            + "l.to_id, l.amount, l.from_balance, l.to_balance, l.reason, l.reference, l.actor_name, pf.name AS from_name, "
            + "pt.name AS to_name";
    private static final String LEDGER_FROM = " FROM eco_ledger l "
            + "LEFT JOIN players pf ON l.from_type = 'player' AND pf.uuid = l.from_id "
            + "LEFT JOIN players pt ON l.to_type = 'player' AND pt.uuid = l.to_id ";

    /** Stops a transaction with a result (rolls back). */
    private static final class Abort extends RuntimeException {
        private final transient TxResult result;

        Abort(TxResult result) {
            super(result.status().name(), null, false, false);
            this.result = result;
        }
    }

    private final Database db;
    private final IntSupplier season;
    private final LongSupplier clock;

    EconomyStore(Database db, IntSupplier season, LongSupplier clock) {
        this.db = db;
        this.season = season;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------------ transactions

    /** Runs a transaction on a database thread. Completes exceptionally only on a database error (after retries). */
    CompletableFuture<TxResult> execute(Transaction tx) {
        return execute(tx, null);
    }

    /**
     * Extra work that must commit or roll back together with a transaction (money notes: insert the note row on
     * withdraw, lock it and mark it used on redeem). Runs inside the database transaction before the accounts are locked.
     */
    @FunctionalInterface
    interface Guard {
        /** @return false to abort: everything is rolled back and the result is {@link TxResult.Status#DUPLICATE} */
        boolean holds(Connection c) throws SQLException;
    }

    /** {@link #execute(Transaction)} with a {@link Guard} in the same database transaction ({@code guard} may be null). */
    CompletableFuture<TxResult> execute(Transaction tx, Guard guard) {
        return db.async(c -> withRetry(() -> run(c, tx, guard)));
    }

    /** Result of {@link #set}: the transaction result and the balance before. */
    record SetOutcome(TxResult result, long previous) {
    }

    /** Sets a balance to an exact value (staff {@code /eco set|reset}); the ledger gets the difference. */
    CompletableFuture<SetOutcome> set(AccountRef account, Currency currency, long value, String reason, String reference, Actor actor) {
        return db.async(c -> withRetry(() -> runSet(c, account, currency, value, reason, reference, actor)));
    }

    @FunctionalInterface
    private interface Attempt<T> {
        T run() throws SQLException;
    }

    private static <T> T withRetry(Attempt<T> attempt) throws SQLException {
        for (int i = 1; ; i++) {
            try {
                return attempt.run();
            } catch (SQLException e) {
                if (i >= MAX_ATTEMPTS || !retryable(e)) {
                    throw e;
                }
                try {
                    Thread.sleep(5L * i + (long) (Math.random() * 10));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    /** Deadlock or lock wait timeout (MariaDB 1213/1205, H2 40001/50200, SQLState class 40). */
    static boolean retryable(SQLException e) {
        String state = e.getSQLState();
        int code = e.getErrorCode();
        return (state != null && state.startsWith("40")) || code == 1213 || code == 1205 || code == 40001 || code == 50200;
    }

    TxResult run(Connection c, Transaction tx) throws SQLException {
        return run(c, tx, null);
    }

    TxResult run(Connection c, Transaction tx, Guard guard) throws SQLException {
        if (tx.problem() != null) {
            return TxResult.of(TxResult.Status.INVALID);
        }
        if (tx.idempotencyKey() != null && keyUsed(c, tx.idempotencyKey())) {
            return TxResult.of(TxResult.Status.DUPLICATE);
        }
        Currency currency = tx.currency();
        List<AccountRef> accounts = new ArrayList<>(2);
        if (tx.from() != null) {
            accounts.add(tx.from());
        }
        if (tx.to() != null) {
            accounts.add(tx.to());
        }
        accounts.sort(AccountRef.LOCK_ORDER);
        for (AccountRef account : accounts) {
            ensureAccount(c, account, currency);
        }
        return inTransaction(c, () -> {
            if (guard != null && !guard.holds(c)) {
                throw new Abort(TxResult.of(TxResult.Status.DUPLICATE));
            }
            for (AccountRef account : accounts) {
                lock(c, account, currency);
            }
            long now = clock.getAsLong();
            if (tx.from() != null && Sql.update(c, DEBIT, tx.amount(), now, tx.from().type().key(), tx.from().owner(),
                    currency.key(), tx.amount()) == 0) {
                throw new Abort(TxResult.of(TxResult.Status.INSUFFICIENT_FUNDS));
            }
            if (tx.to() != null && Sql.update(c, CREDIT, tx.amount(), now, tx.to().type().key(), tx.to().owner(),
                    currency.key(), Long.MAX_VALUE - tx.amount()) == 0) {
                throw new Abort(TxResult.of(TxResult.Status.OVERFLOW));
            }
            Long fromBalance = tx.from() == null ? null : read(c, tx.from(), currency);
            Long toBalance = tx.to() == null ? null : read(c, tx.to(), currency);
            long id = ledger(c, now, currency, tx.from(), tx.to(), tx.amount(), fromBalance, toBalance, tx.reason(),
                    tx.reference(), tx.idempotencyKey(), tx.actor());
            return new TxResult(TxResult.Status.SUCCESS, id, fromBalance, toBalance);
        });
    }

    SetOutcome runSet(Connection c, AccountRef account, Currency currency, long value, String reason, String reference, Actor actor)
            throws SQLException {
        if (value < 0 || !TxReason.isValid(reason)) {
            return new SetOutcome(TxResult.of(TxResult.Status.INVALID), 0);
        }
        ensureAccount(c, account, currency);
        long[] previous = new long[1];
        TxResult result = inTransaction(c, () -> {
            long old = lock(c, account, currency);
            previous[0] = old;
            long now = clock.getAsLong();
            if (old == value) {
                return new TxResult(TxResult.Status.SUCCESS, 0, null, value);
            }
            if (Sql.update(c, COMPARE_AND_SET, value, now, account.type().key(), account.owner(), currency.key(), old) == 0) {
                throw new SQLException("Balance changed while locked", "40001", 40001);
            }
            if (value > old) {
                long id = ledger(c, now, currency, null, account, value - old, null, value, reason, reference, null, actor);
                return new TxResult(TxResult.Status.SUCCESS, id, null, value);
            }
            long id = ledger(c, now, currency, account, null, old - value, value, null, reason, reference, null, actor);
            return new TxResult(TxResult.Status.SUCCESS, id, value, null);
        });
        return new SetOutcome(result, previous[0]);
    }

    @FunctionalInterface
    private interface Work {
        TxResult run() throws SQLException;
    }

    private static TxResult inTransaction(Connection c, Work work) throws SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            TxResult result = work.run();
            c.commit();
            return result;
        } catch (Abort abort) {
            c.rollback();
            return abort.result;
        } catch (SQLException | RuntimeException e) {
            try {
                c.rollback();
            } catch (SQLException rollback) {
                e.addSuppressed(rollback);
            }
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    private static boolean keyUsed(Connection c, String key) throws SQLException {
        return Sql.queryOne(c, "SELECT id FROM eco_ledger WHERE idem_key = ?", rs -> rs.getLong(1), key).isPresent();
    }

    /** Creates the account row (balance 0) if it does not exist yet. Autocommit, outside the transaction. */
    private void ensureAccount(Connection c, AccountRef account, Currency currency) throws SQLException {
        if (Sql.queryOne(c, READ, rs -> rs.getLong(1), account.type().key(), account.owner(), currency.key()).isPresent()) {
            return;
        }
        try {
            Sql.update(c, "INSERT INTO eco_accounts (owner_type, owner_id, currency, balance, updated_at) VALUES (?, ?, ?, 0, ?)",
                    account.type().key(), account.owner(), currency.key(), clock.getAsLong());
        } catch (SQLException e) {
            if (!isDuplicate(e)) {
                throw e;
            }
            // created by a concurrent transaction: fine
        }
    }

    private static long lock(Connection c, AccountRef account, Currency currency) throws SQLException {
        return Sql.queryOne(c, LOCK, rs -> rs.getLong(1), account.type().key(), account.owner(), currency.key())
                .orElseThrow(() -> new SQLException("Account " + account + " " + currency.key() + " is missing"));
    }

    private static long read(Connection c, AccountRef account, Currency currency) throws SQLException {
        return Sql.queryOne(c, READ, rs -> rs.getLong(1), account.type().key(), account.owner(), currency.key())
                .orElseThrow(() -> new SQLException("Account " + account + " " + currency.key() + " is missing"));
    }

    private long ledger(Connection c, long now, Currency currency, AccountRef from, AccountRef to, long amount, Long fromBalance,
                        Long toBalance, String reason, String reference, String key, Actor actor) throws SQLException {
        try {
            return Sql.insertReturningId(c, INSERT_LEDGER, now, season.getAsInt(), currency.key(),
                    from == null ? null : from.type().key(), from == null ? null : from.owner(),
                    to == null ? null : to.type().key(), to == null ? null : to.owner(),
                    amount, fromBalance, toBalance, reason, reference, key, actor.uuid(), actor.name());
        } catch (SQLException e) {
            if (key != null && isDuplicate(e)) {
                throw new Abort(TxResult.of(TxResult.Status.DUPLICATE));
            }
            throw e;
        }
    }

    static boolean isDuplicate(SQLException e) {
        return e instanceof SQLIntegrityConstraintViolationException
                || (e.getSQLState() != null && e.getSQLState().startsWith("23"));
    }

    // ------------------------------------------------------------------------------------------------ reads

    CompletableFuture<Long> balance(AccountRef account, Currency currency) {
        return db.async(c -> Sql.queryOne(c, READ, rs -> rs.getLong(1), account.type().key(), account.owner(), currency.key())
                .orElse(0L));
    }

    CompletableFuture<Map<Currency, Long>> balances(AccountRef account) {
        return db.async(c -> balancesNow(c, account));
    }

    static Map<Currency, Long> balancesNow(Connection c, AccountRef account) throws SQLException {
        Map<Currency, Long> out = new EnumMap<>(Currency.class);
        for (Currency currency : Currency.values()) {
            out.put(currency, 0L);
        }
        for (Object[] row : Sql.query(c, "SELECT currency, balance FROM eco_accounts WHERE owner_type = ? AND owner_id = ?",
                rs -> new Object[]{rs.getString(1), rs.getLong(2)}, account.type().key(), account.owner())) {
            Currency.byKey((String) row[0]).ifPresent(cur -> out.put(cur, (Long) row[1]));
        }
        return out;
    }

    /** One {@code /baltop} line. {@code player} is the player (account) UUID. */
    record TopEntry(UUID player, String name, long balance) {
    }

    /** Richest accounts in a currency (balance above 0), richest first; ids are account ids (profile keys). */
    CompletableFuture<List<TopEntry>> top(Currency currency, int limit) {
        return db.async(c -> topNow(c, currency, limit));
    }

    static List<TopEntry> topNow(Connection c, Currency currency, int limit) throws SQLException {
        return Sql.query(c, "SELECT a.owner_id, a.balance, p.name FROM eco_accounts a "
                        + "LEFT JOIN players p ON p.uuid = a.owner_id "
                        + "WHERE a.currency = ? AND a.owner_type = ? AND a.balance > 0 ORDER BY a.balance DESC LIMIT ?",
                rs -> new TopEntry(Sql.uuid(rs, "owner_id"), rs.getString("name"), rs.getLong("balance")),
                currency.key(), AccountType.PLAYER.key(), limit);
    }

    /**
     * {@code /baltop} with owner decision P7: one line per player from their best live profile. Gems are already per
     * player. Reads a few times more rows than shown, because one player can have up to 3 profiles in the list.
     */
    CompletableFuture<List<TopEntry>> topBest(Currency currency, int size, ProfileService profiles) {
        return db.async(c -> topBestNow(c, currency, size, profiles));
    }

    static List<TopEntry> topBestNow(Connection c, Currency currency, int size, ProfileService profiles) throws SQLException {
        if (currency == Currency.GEMS) {
            return topNow(c, currency, size);
        }
        List<TopEntry> raw = topNow(c, currency, size * SlotRules.MAX_SLOTS + 10);
        Map<UUID, Long> values = new LinkedHashMap<>();
        for (TopEntry e : raw) {
            values.put(e.player(), e.balance());
        }
        List<BestProfile.Entry> best = profiles.bestPerOwner(values);
        if (best.size() > size) {
            best = best.subList(0, size);
        }
        Map<UUID, String> names = names(c, best.stream().map(BestProfile.Entry::owner).toList());
        List<TopEntry> out = new ArrayList<>(best.size());
        for (BestProfile.Entry e : best) {
            out.add(new TopEntry(e.owner(), names.get(e.owner()), e.value()));
        }
        return out;
    }

    /** Current names of players (missing ones are left out). */
    static Map<UUID, String> names(Connection c, Collection<UUID> players) throws SQLException {
        Map<UUID, String> out = new HashMap<>();
        for (UUID id : new LinkedHashSet<>(players)) {
            Sql.queryOne(c, "SELECT name FROM players WHERE uuid = ?", rs -> rs.getString(1), id).ifPresent(n -> out.put(id, n));
        }
        return out;
    }

    /** One page of an account's ledger. */
    record LedgerPage(List<LedgerEntry> rows, long total) {
    }

    /** Profile key → player UUID, for ledger names (set by the module; identity in tests). */
    private volatile UnaryOperator<UUID> owners = UnaryOperator.identity();

    void owners(UnaryOperator<UUID> owners) {
        this.owners = owners == null ? UnaryOperator.identity() : owners;
    }

    /** An account's ledger rows matching {@code filter}, newest first. */
    CompletableFuture<LedgerPage> history(AccountRef account, LedgerFilter filter, int offset, int limit) {
        return history(account, null, filter, offset, limit);
    }

    /**
     * A profile's ledger: rows of {@code account} plus the gem rows of {@code gemsAccount} (its player; {@code null} or
     * equal = none extra), newest first.
     */
    CompletableFuture<LedgerPage> history(AccountRef account, AccountRef gemsAccount, LedgerFilter filter, int offset, int limit) {
        UnaryOperator<UUID> map = owners;
        return db.async(c -> withNames(c, historyNow(c, account, gemsAccount, filter, offset, limit, clock.getAsLong()), map));
    }

    static LedgerPage historyNow(Connection c, AccountRef account, LedgerFilter filter, int offset, int limit, long now)
            throws SQLException {
        return historyNow(c, account, null, filter, offset, limit, now);
    }

    static LedgerPage historyNow(Connection c, AccountRef account, AccountRef gemsAccount, LedgerFilter filter, int offset, int limit,
                                 long now) throws SQLException {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE (((l.from_type = ? AND l.from_id = ?) OR (l.to_type = ? AND l.to_id = ?))");
        params.add(account.type().key());
        params.add(account.owner());
        params.add(account.type().key());
        params.add(account.owner());
        if (gemsAccount != null && !gemsAccount.equals(account)) {
            where.append(" OR (l.currency = ? AND ((l.from_type = ? AND l.from_id = ?) OR (l.to_type = ? AND l.to_id = ?)))");
            params.add(Currency.GEMS.key());
            params.add(gemsAccount.type().key());
            params.add(gemsAccount.owner());
            params.add(gemsAccount.type().key());
            params.add(gemsAccount.owner());
        }
        where.append(")");
        if (filter.currency() != null) {
            where.append(" AND l.currency = ?");
            params.add(filter.currency().key());
        }
        if (filter.reason() != null) {
            // "ah" matches ah, ah_buy, ah_sale … ("_" is escaped: it is a LIKE wildcard)
            where.append(" AND (l.reason = ? OR l.reason LIKE ?)");
            params.add(filter.reason());
            params.add(filter.reason().replace("_", "\\_") + "\\_%");
        }
        if (filter.since() != null) {
            where.append(" AND l.created_at >= ?");
            params.add(now - filter.since().toMillis());
        }
        long total = Sql.queryOne(c, "SELECT COUNT(*)" + LEDGER_FROM + where, rs -> rs.getLong(1), params.toArray()).orElse(0L);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(Math.max(1, limit));
        pageParams.add(Math.max(0, offset));
        List<LedgerEntry> rows = Sql.query(c, "SELECT " + LEDGER_COLUMNS + LEDGER_FROM + where + " ORDER BY l.id DESC LIMIT ? OFFSET ?",
                EconomyStore::entry, pageParams.toArray());
        return new LedgerPage(rows, total);
    }

    /** Fills the names of profile accounts (their key is not a player UUID) with their player's name. */
    static LedgerPage withNames(Connection c, LedgerPage page, UnaryOperator<UUID> owners) throws SQLException {
        Map<UUID, UUID> wanted = new HashMap<>();
        for (LedgerEntry e : page.rows()) {
            if (e.fromName() == null && e.from() != null && e.from().isPlayer()) {
                wanted.put(e.from().owner(), owners.apply(e.from().owner()));
            }
            if (e.toName() == null && e.to() != null && e.to().isPlayer()) {
                wanted.put(e.to().owner(), owners.apply(e.to().owner()));
            }
        }
        wanted.values().removeIf(java.util.Objects::isNull);
        if (wanted.isEmpty()) {
            return page;
        }
        Map<UUID, String> names = names(c, wanted.values());
        List<LedgerEntry> rows = new ArrayList<>(page.rows().size());
        for (LedgerEntry e : page.rows()) {
            String from = e.fromName() != null || e.from() == null ? e.fromName() : names.get(wanted.get(e.from().owner()));
            String to = e.toName() != null || e.to() == null ? e.toName() : names.get(wanted.get(e.to().owner()));
            rows.add(new LedgerEntry(e.id(), e.time(), e.season(), e.currency(), e.from(), e.to(), e.amount(), e.fromBalance(),
                    e.toBalance(), e.reason(), e.reference(), e.actorName(), from, to));
        }
        return new LedgerPage(rows, page.total());
    }

    /** One ledger row by id (tests, staff tools). */
    static Optional<LedgerEntry> entry(Connection c, long id) throws SQLException {
        return Sql.queryOne(c, "SELECT " + LEDGER_COLUMNS + LEDGER_FROM + "WHERE l.id = ?", EconomyStore::entry, id);
    }

    private static LedgerEntry entry(ResultSet rs) throws SQLException {
        return new LedgerEntry(rs.getLong("id"), rs.getLong("created_at"), rs.getInt("season_id"),
                Currency.byKey(rs.getString("currency")).orElse(Currency.MONEY),
                ref(rs.getString("from_type"), Sql.uuid(rs, "from_id")), ref(rs.getString("to_type"), Sql.uuid(rs, "to_id")),
                rs.getLong("amount"), Sql.nullableLong(rs, "from_balance"), Sql.nullableLong(rs, "to_balance"),
                rs.getString("reason"), rs.getString("reference"), rs.getString("actor_name"),
                rs.getString("from_name"), rs.getString("to_name"));
    }

    private static AccountRef ref(String type, UUID owner) {
        if (type == null || owner == null) {
            return null;
        }
        return AccountType.byKey(type).map(t -> new AccountRef(t, owner)).orElse(null);
    }
}

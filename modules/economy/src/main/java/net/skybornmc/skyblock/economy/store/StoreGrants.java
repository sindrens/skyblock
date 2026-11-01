package net.skybornmc.skyblock.economy.store;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Credits bought on Tebex (CREDIT-SHOP 5.2 and 5.3): the grant and the chargeback, each one guarded ledger transaction.
 * The grant writes the bought credits and the {@code store_purchases} receipt together; a replay of the same
 * transaction and package (Tebex resend, our retry) changes nothing (receipt primary key + ledger idempotency key). The
 * chargeback takes back at most the player's bought balance and records what is still owed. No Bukkit here, so H2 tests
 * cover it; the command layer ({@code EconomyModule}) does console checks, messages, alerts and the event.
 */
public final class StoreGrants {

    /** Tebex transaction ids: letters, digits and {@code - _ . :}, at most 64 (the column). */
    private static final Pattern TX = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");
    private static final int RECENT = 200;
    private static final int CHARGEBACK_TRIES = 3;

    public enum Outcome { GRANTED, DUPLICATE, FAILED }

    /** Result of a grant; {@code balanceAfter} = bought credits after it (null unless GRANTED). */
    public record Grant(Outcome outcome, Long balanceAfter) {
    }

    /** Result of a chargeback: rows closed now, bought credits taken, credits still owed (spent before the chargeback). */
    public record Chargeback(int rows, long taken, long owed, boolean failed) {
        public static final Chargeback NONE = new Chargeback(0, 0, 0, false);
    }

    private record Row(String packageId, long credits) {
    }

    private final EconomyService economy;
    private final Database db;
    private final LongSupplier clock;
    /** Last transaction ids (newest first) for tab completion of /credit chargeback. Main thread. */
    private final Deque<String> recent = new ArrayDeque<>();

    public StoreGrants(EconomyService economy, Database db, LongSupplier clock) {
        this.economy = economy;
        this.db = db;
        this.clock = clock;
    }

    /** A Tebex transaction id we accept (also blocks SQL-looking or empty input from the console). */
    public static boolean validTx(String tx) {
        return tx != null && TX.matcher(tx).matches();
    }

    /**
     * The player of a Tebex {@code {id}}: a UUID with or without dashes (Java), or a 12 to 16 digit decimal XUID (Bedrock,
     * the Floodgate UUID {@code new UUID(0, xuid)}). Empty = treat it as a player name.
     */
    public static Optional<UUID> parseId(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.matches("[0-9]{12,16}")) {
            return Optional.of(new UUID(0, Long.parseLong(s)));
        }
        if (s.matches("[0-9a-f]{32}")) {
            s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + s.substring(20);
        }
        if (s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return Optional.of(UUID.fromString(s));
        }
        return Optional.empty();
    }

    /** The ledger idempotency key of a grant (also the reason a replay is a DUPLICATE). */
    static String grantKey(String tx, String packageId) {
        return "tebex:" + tx + ":" + packageId;
    }

    static String chargebackKey(String tx, String packageId) {
        return "tebex-cb:" + tx + ":" + packageId;
    }

    /** Grants {@code pack} to {@code player} for Tebex transaction {@code tx}. Never completes exceptionally. */
    public CompletableFuture<Grant> grant(UUID player, CreditPackages.Pack pack, String tx) {
        if (player == null || pack == null || !validTx(tx)) {
            return CompletableFuture.completedFuture(new Grant(Outcome.FAILED, null));
        }
        long now = clock.getAsLong();
        TxGuard guard = c -> {
            if (Sql.queryOne(c, "SELECT credits FROM store_purchases WHERE tx_id = ? AND package_id = ?",
                    rs -> rs.getLong("credits"), tx, pack.id()).isPresent()) {
                return false;
            }
            Sql.update(c, "INSERT INTO store_purchases (tx_id, package_id, player_uuid, credits, usd_cents, state, taken, granted_at) "
                    + "VALUES (?, ?, ?, ?, ?, 'granted', 0, ?)", tx, pack.id(), player, pack.credits(), pack.usdCents(), now);
            return true;
        };
        Transaction t = Transaction.deposit(AccountRef.player(player), Currency.byCanonicalKey("credits").orElseThrow(),
                        pack.credits(), TxReason.STORE_GRANT)
                .actor(Actor.system("tebex"))
                .reference("tebex:" + tx)
                .idempotencyKey(grantKey(tx, pack.id()))
                .detail("package", pack.id())
                .detail("usd_cents", pack.usdCents());
        return economy.executeGuarded(t, guard).handle((r, error) -> {
            if (error != null || r == null) {
                return new Grant(Outcome.FAILED, null);
            }
            return switch (r.status()) {
                case SUCCESS -> new Grant(Outcome.GRANTED, r.toBalance());
                case DUPLICATE -> new Grant(Outcome.DUPLICATE, null);
                default -> new Grant(Outcome.FAILED, null);
            };
        });
    }

    /**
     * Takes back the bought credits of every still granted package of {@code tx} for {@code player}: per package at most
     * the bought balance at that moment (earned credits are never touched, C1-R1-35); the rest is {@code owed}. A second
     * call finds no granted row and returns {@link Chargeback#NONE}. Never completes exceptionally.
     */
    public CompletableFuture<Chargeback> chargeback(UUID player, String tx) {
        if (player == null || !validTx(tx)) {
            return CompletableFuture.completedFuture(Chargeback.NONE);
        }
        return db.async(c -> Sql.query(c, "SELECT package_id, credits FROM store_purchases WHERE player_uuid = ? AND tx_id = ? "
                        + "AND state = 'granted' ORDER BY package_id", rs -> new Row(rs.getString("package_id"), rs.getLong("credits")),
                player, tx))
                .thenCompose(rows -> {
                    CompletableFuture<Chargeback> chain = CompletableFuture.completedFuture(Chargeback.NONE);
                    for (Row row : rows) {
                        chain = chain.thenCompose(sum -> takeBack(player, tx, row, 0).thenApply(one -> new Chargeback(
                                sum.rows() + one.rows(), sum.taken() + one.taken(), sum.owed() + one.owed(), sum.failed() || one.failed())));
                    }
                    return chain;
                })
                .exceptionally(error -> new Chargeback(0, 0, 0, true));
    }

    private CompletableFuture<Chargeback> takeBack(UUID player, String tx, Row row, int attempt) {
        return economy.credits(player).thenCompose(view -> {
            long take = Math.min(row.credits(), Math.max(0, view.bought()));
            long now = clock.getAsLong();
            TxGuard close = c -> Sql.update(c, "UPDATE store_purchases SET state = 'chargeback', taken = ?, closed_at = ? "
                    + "WHERE tx_id = ? AND package_id = ? AND state = 'granted'", take, now, tx, row.packageId()) == 1;
            if (take <= 0) {
                // Nothing bought is left: only close the receipt (everything is owed).
                return db.async(close::holds).thenApply(closed -> closed
                        ? new Chargeback(1, 0, row.credits(), false) : Chargeback.NONE);
            }
            Transaction t = Transaction.withdraw(AccountRef.player(player), Currency.byCanonicalKey("credits").orElseThrow(),
                            take, TxReason.STORE_CHARGEBACK)
                    .actor(Actor.system("tebex"))
                    .reference("tebex:" + tx)
                    .idempotencyKey(chargebackKey(tx, row.packageId()))
                    .detail("package", row.packageId())
                    .detail("owed", row.credits() - take);
            return economy.executeGuarded(t, close).thenCompose(r -> {
                if (r.status() == TxResult.Status.SUCCESS) {
                    return CompletableFuture.completedFuture(new Chargeback(1, take, row.credits() - take, false));
                }
                if (r.status() == TxResult.Status.DUPLICATE) {
                    return CompletableFuture.completedFuture(Chargeback.NONE); // closed by another call meanwhile
                }
                if (r.status() == TxResult.Status.INSUFFICIENT_FUNDS && attempt + 1 < CHARGEBACK_TRIES) {
                    return takeBack(player, tx, row, attempt + 1); // spent between the read and the take: read again
                }
                return CompletableFuture.completedFuture(new Chargeback(0, 0, 0, true));
            });
        });
    }

    /** Loads the newest transaction ids for tab completion (call once at enable). */
    public CompletableFuture<List<String>> loadRecent() {
        return db.async(c -> Sql.query(c, "SELECT tx_id FROM store_purchases ORDER BY granted_at DESC LIMIT " + RECENT,
                rs -> rs.getString("tx_id")));
    }

    /** Main thread: replaces the remembered ids (newest first). */
    public void remember(List<String> newestFirst) {
        recent.clear();
        for (String tx : newestFirst) {
            if (!recent.contains(tx) && recent.size() < RECENT) {
                recent.addLast(tx);
            }
        }
    }

    /** Main thread: a new grant's id goes first. */
    public void remember(String tx) {
        recent.remove(tx);
        recent.addFirst(tx);
        while (recent.size() > RECENT) {
            recent.removeLast();
        }
    }

    /** Main thread: the remembered ids, newest first. */
    public List<String> recent() {
        return new ArrayList<>(recent);
    }
}

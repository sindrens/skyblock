package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Money notes (M3.2), database side. Every path is one database transaction:
 * <ul>
 *   <li><b>withdraw</b>: insert the note row (status active) + take the money (reason {@code note_withdraw},
 *       idempotency key per note). Not enough money → the row insert is rolled back too.</li>
 *   <li><b>redeem</b>: lock the note row ({@code FOR UPDATE}), check it is still active, mark it redeemed + deposit
 *       (reason {@code note_redeem}, idempotency key per note). Two redeems of copies of the same note: the second one
 *       waits for the lock, then sees "redeemed" and changes nothing.</li>
 *   <li><b>admin</b>: insert an active row without taking money ({@code /eco note}).</li>
 * </ul>
 * Futures never complete exceptionally: database errors become {@link Outcome#ERROR}. No Bukkit here (unit-tested on H2).
 */
final class NoteService {

    private static final String COLUMNS = "id, amount, currency, kind, created_by, created_name, created_at, note, redeemed_by, "
            + "redeemed_name, redeemed_at, status";
    private static final String INSERT = "INSERT INTO money_notes (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, ?)";
    private static final String LOCK_STATUS = "SELECT status FROM money_notes WHERE id = ? FOR UPDATE";
    private static final String MARK_REDEEMED = "UPDATE money_notes SET status = ?, redeemed_by = ?, redeemed_name = ?, redeemed_at = ? "
            + "WHERE id = ? AND status = ?";
    private static final String MARK_VOID = "UPDATE money_notes SET status = ? WHERE id = ? AND status = ?";

    /** Result of a note operation. */
    enum Outcome {
        /** Done. */
        SUCCESS,
        /** Withdraw: the player has less than the amount. Nothing changed. */
        INSUFFICIENT_FUNDS,
        /** Redeem: the note was already cashed (this item is a copy). Nothing changed. */
        ALREADY_REDEEMED,
        /** Redeem: no note with this id exists (forged or spawned item). Nothing changed. */
        UNKNOWN,
        /** Redeem: the note was voided (never handed out). Nothing changed. */
        VOID,
        /** Redeem: the balance would overflow. Nothing changed. */
        OVERFLOW,
        /** Redeem: the note was made by another profile of the same player (owner P4). Nothing changed. */
        OWN_PROFILE,
        /** Database error or invalid input. Nothing changed. */
        ERROR
    }

    /**
     * @param note    the note as it is now (after the operation), when known
     * @param balance the player's balance after a successful withdraw/redeem
     */
    record Result(Outcome outcome, MoneyNote note, Long balance) {
        static Result of(Outcome outcome, MoneyNote note) {
            return new Result(outcome, note, null);
        }

        boolean ok() {
            return outcome == Outcome.SUCCESS;
        }
    }

    /**
     * Owner decision P4: true (= refuse, already audited) when a withdraw note made by profile {@code createdBy} is
     * redeemed by another profile {@code redeemer} of the same player.
     */
    @FunctionalInterface
    interface OwnProfileCheck {
        boolean blocked(UUID createdBy, UUID redeemer, Actor actor);

        OwnProfileCheck NONE = (a, b, c) -> false;
    }

    private final Database db;
    private final EconomyServiceImpl economy;
    private final LongSupplier clock;
    private final Logger log;
    private final OwnProfileCheck ownProfile;

    NoteService(Database db, EconomyServiceImpl economy, LongSupplier clock, Logger log) {
        this(db, economy, clock, log, OwnProfileCheck.NONE);
    }

    NoteService(Database db, EconomyServiceImpl economy, LongSupplier clock, Logger log, OwnProfileCheck ownProfile) {
        this.db = db;
        this.economy = economy;
        this.clock = clock;
        this.log = log;
        this.ownProfile = ownProfile;
    }

    // ------------------------------------------------------------------------------------------------ withdraw

    /**
     * Takes {@code amount} from the player and records note {@code id} in one transaction. The caller hands out the item
     * only after {@link Outcome#SUCCESS}.
     *
     * @param player the profile key the money is taken from (stored as the note's creator)
     */
    CompletableFuture<Result> withdraw(UUID id, UUID player, String playerName, Currency currency, long amount) {
        return withdraw(id, player, player, playerName, currency, amount);
    }

    /** {@link #withdraw(UUID, UUID, String, Currency, long)} with the player (audit actor) apart from the profile key. */
    CompletableFuture<Result> withdraw(UUID id, UUID player, UUID actor, String playerName, Currency currency, long amount) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(Result.of(Outcome.ERROR, null));
        }
        MoneyNote note = new MoneyNote(id, amount, currency, MoneyNote.Kind.WITHDRAW, player, clip(playerName, 32), clock.getAsLong(),
                null, null, null, null, MoneyNote.Status.ACTIVE);
        Transaction tx = Transaction.withdraw(AccountRef.player(player), currency, amount, TxReason.NOTE_WITHDRAW)
                .actor(Actor.player(actor, playerName))
                .reference(id.toString())
                .idempotencyKey(MoneyNote.withdrawKey(id))
                .detail("note_id", id.toString())
                .detail("target_name", playerName);
        return economy.execute(tx, c -> insert(c, note)).thenApply(result -> switch (result.status()) {
            case SUCCESS -> new Result(Outcome.SUCCESS, note, result.fromBalance());
            case INSUFFICIENT_FUNDS -> Result.of(Outcome.INSUFFICIENT_FUNDS, null);
            default -> Result.of(Outcome.ERROR, null);
        });
    }

    // ------------------------------------------------------------------------------------------------ admin

    /** {@code /eco note}: records an active note without taking money. */
    CompletableFuture<Result> createAdmin(UUID id, Currency currency, long amount, Actor staff, String reason) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(Result.of(Outcome.ERROR, null));
        }
        MoneyNote note = new MoneyNote(id, amount, currency, MoneyNote.Kind.ADMIN, staff.uuid(), clip(staff.name(), 32),
                clock.getAsLong(), clip(reason, 64), null, null, null, MoneyNote.Status.ACTIVE);
        return guard(db.async(c -> insert(c, note) ? Result.of(Outcome.SUCCESS, note) : Result.of(Outcome.ERROR, null)), "create admin note " + id);
    }

    /** Voids an active note that was never handed out (the target left). True if it was voided. */
    CompletableFuture<Boolean> voidNote(UUID id) {
        return db.async(c -> Sql.update(c, MARK_VOID, MoneyNote.Status.VOID.key(), id, MoneyNote.Status.ACTIVE.key()) == 1)
                .exceptionally(e -> {
                    log.log(Level.SEVERE, "Money notes: could not void note " + id, Database.cause(e));
                    return false;
                });
    }

    // ------------------------------------------------------------------------------------------------ redeem

    /** Cashes note {@code id} for {@code player} (a profile key): marks it redeemed and deposits its amount, atomically. */
    CompletableFuture<Result> redeem(UUID id, UUID player, String playerName) {
        return redeem(id, player, player, playerName);
    }

    /** {@link #redeem(UUID, UUID, String)} with the player (audit actor) apart from the profile key. */
    CompletableFuture<Result> redeem(UUID id, UUID player, UUID actor, String playerName) {
        return find(id).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(Result.of(Outcome.UNKNOWN, null));
            }
            MoneyNote note = found.get();
            if (note.status() == MoneyNote.Status.REDEEMED) {
                return CompletableFuture.completedFuture(Result.of(Outcome.ALREADY_REDEEMED, note));
            }
            if (note.status() == MoneyNote.Status.VOID) {
                return CompletableFuture.completedFuture(Result.of(Outcome.VOID, note));
            }
            // P4: a note withdrawn on one profile can't be cashed on another profile of the same player (the creator
            // never changes, so checking before the transaction is safe). Staff notes are created money, not a transfer.
            if (note.kind() == MoneyNote.Kind.WITHDRAW && note.createdBy() != null
                    && ownProfile.blocked(note.createdBy(), player, Actor.player(actor, playerName))) {
                return CompletableFuture.completedFuture(Result.of(Outcome.OWN_PROFILE, note));
            }
            long now = clock.getAsLong();
            Transaction tx = Transaction.deposit(AccountRef.player(player), note.currency(), note.amount(), TxReason.NOTE_REDEEM)
                    .actor(Actor.player(actor, playerName))
                    .reference(id.toString())
                    .idempotencyKey(MoneyNote.redeemKey(id))
                    .detail("note_id", id.toString())
                    .detail("note_kind", note.kind().key())
                    .detail("signed_by", note.createdName())
                    .detail("target_name", playerName);
            return economy.execute(tx, c -> markRedeemed(c, id, player, playerName, now)).thenCompose(result -> switch (result.status()) {
                case SUCCESS -> CompletableFuture.completedFuture(new Result(Outcome.SUCCESS,
                        redeemed(note, player, playerName, now), result.toBalance()));
                // Lost the race against another copy (row lock) or the key was used: read who got it for the alert.
                case DUPLICATE -> find(id).thenApply(now2 -> Result.of(Outcome.ALREADY_REDEEMED, now2.orElse(note)));
                case OVERFLOW -> CompletableFuture.completedFuture(Result.of(Outcome.OVERFLOW, note));
                default -> CompletableFuture.completedFuture(Result.of(Outcome.ERROR, note));
            });
        }).exceptionally(e -> {
            log.log(Level.SEVERE, "Money notes: redeeming note " + id + " for " + playerName + " failed", Database.cause(e));
            return Result.of(Outcome.ERROR, null);
        });
    }

    private static MoneyNote redeemed(MoneyNote n, UUID by, String name, long at) {
        return new MoneyNote(n.id(), n.amount(), n.currency(), n.kind(), n.createdBy(), n.createdName(), n.createdAt(), n.note(),
                by, clip(name, 32), at, MoneyNote.Status.REDEEMED);
    }

    // ------------------------------------------------------------------------------------------------ reads

    /** The note with this id, if any. */
    CompletableFuture<Optional<MoneyNote>> find(UUID id) {
        return db.async(c -> Sql.queryOne(c, "SELECT " + COLUMNS + " FROM money_notes WHERE id = ?", NoteService::row, id));
    }

    // ------------------------------------------------------------------------------------------------ SQL (inside transactions)

    private static boolean insert(Connection c, MoneyNote n) throws SQLException {
        try {
            return Sql.update(c, INSERT, n.id(), n.amount(), n.currency().key(), n.kind().key(), n.createdBy(), n.createdName(),
                    n.createdAt(), n.note(), n.status().key()) == 1;
        } catch (SQLException e) {
            if (EconomyStore.isDuplicate(e)) {
                return false; // the id is taken (never happens with random UUIDs; refused instead of overwriting)
            }
            throw e;
        }
    }

    /** Locks the row and flips active → redeemed. False (= roll back the deposit) if it is not active any more. */
    private static boolean markRedeemed(Connection c, UUID id, UUID by, String name, long at) throws SQLException {
        Optional<String> status = Sql.queryOne(c, LOCK_STATUS, rs -> rs.getString(1), id);
        if (status.isEmpty() || MoneyNote.Status.byKey(status.get()) != MoneyNote.Status.ACTIVE) {
            return false;
        }
        return Sql.update(c, MARK_REDEEMED, MoneyNote.Status.REDEEMED.key(), by, clip(name, 32), at, id,
                MoneyNote.Status.ACTIVE.key()) == 1;
    }

    private static MoneyNote row(ResultSet rs) throws SQLException {
        return new MoneyNote(Sql.uuid(rs, "id"), rs.getLong("amount"),
                Currency.byKey(rs.getString("currency")).orElse(Currency.MONEY),
                MoneyNote.Kind.byKey(rs.getString("kind")), Sql.uuid(rs, "created_by"), rs.getString("created_name"),
                rs.getLong("created_at"), rs.getString("note"), Sql.uuid(rs, "redeemed_by"), rs.getString("redeemed_name"),
                Sql.nullableLong(rs, "redeemed_at"), MoneyNote.Status.byKey(rs.getString("status")));
    }

    private CompletableFuture<Result> guard(CompletableFuture<Result> future, String what) {
        return future.exceptionally(e -> {
            log.log(Level.SEVERE, "Money notes: " + what + " failed", Database.cause(e));
            return Result.of(Outcome.ERROR, null);
        });
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}

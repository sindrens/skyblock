package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.tools.ToolPerk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs an {@link OpPlan} atomically (ENCHANTS §4.2 step 5, §9): one database transaction, through the economy when
 * crystals move ({@code EconomyService#executeGuarded}), so the books, the daily counter, the tool perks and the crystals
 * commit together or not at all. Futures never complete exceptionally. No Bukkit (unit-tested on H2).
 */
final class OpRunner {

    /** Pays or refunds crystals together with extra SQL (the economy's guarded transaction). */
    @FunctionalInterface
    interface Payer {
        CompletableFuture<TxResult> run(Transaction transaction, TxGuard guard);
    }

    /** Stores a tool's perks inside the transaction ({@code ToolService#savePerks}). */
    @FunctionalInterface
    interface PerkSaver {
        void save(Connection c, UUID toolId, List<ToolPerk> perks) throws SQLException;
    }

    /** E5: reads (locked) and stores a wardrobe piece's armor enchants inside the transaction ({@code WardrobeService}). */
    interface PieceStore {
        /** The piece's enchants with the entry row locked; empty when the entry is not active or not this profile's. */
        Optional<List<ToolPerk>> lock(Connection c, UUID profile, UUID entryId, net.skybornmc.skyblock.common.wardrobe.ArmorPiece piece) throws SQLException;

        void save(Connection c, UUID entryId, net.skybornmc.skyblock.common.wardrobe.ArmorPiece piece, List<ToolPerk> enchants) throws SQLException;
    }

    enum Status {
        OK,
        /** E5: the set piece changed or is gone (merged, removed, another profile). Nothing changed. */
        PIECE_CHANGED,
        /** Not enough crystals. Nothing changed. */
        NOT_ENOUGH,
        /** A book/scroll row is used up, void or unknown: the item is a copy. Nothing changed. */
        BOOK_INVALID,
        /** The daily roll limit is reached. Nothing changed. */
        LIMIT,
        /** Database or economy error. Nothing changed. */
        ERROR
    }

    /**
     * @param badBook the book whose row failed (BOOK_INVALID), else null
     * @param balance crystals after the payment/refund, when known
     */
    record Result(Status status, UUID badBook, Long balance) {
        static Result of(Status s) {
            return new Result(s, null, null);
        }

        boolean ok() {
            return status == Status.OK;
        }
    }

    /** Why the guard said no (reset on every retry). */
    private static final class Failure {
        Status status;
        UUID book;

        void reset() {
            status = null;
            book = null;
        }

        void set(Status s, UUID id) {
            status = s;
            book = id;
        }
    }

    /** Thrown inside a plain transaction to roll it back when the guard says no. */
    private static final class Refused extends RuntimeException {
        Refused() {
            super("refused", null, false, false);
        }
    }

    private final Database db;
    private final BookStore store;
    private final Payer payer;
    private final PerkSaver perks;
    private final PieceStore pieces;
    private final LongSupplier clock;
    private final Logger log;

    OpRunner(Database db, BookStore store, Payer payer, PerkSaver perks, LongSupplier clock, Logger log) {
        this(db, store, payer, perks, null, clock, log);
    }

    OpRunner(Database db, BookStore store, Payer payer, PerkSaver perks, PieceStore pieces, LongSupplier clock, Logger log) {
        this.db = db;
        this.store = store;
        this.payer = payer;
        this.perks = perks;
        this.pieces = pieces;
        this.clock = clock;
        this.log = log;
    }

    // ------------------------------------------------------------------ run

    CompletableFuture<Result> run(OpPlan plan) {
        Failure failure = new Failure();
        TxGuard guard = c -> apply(c, plan, failure);
        CompletableFuture<Result> future;
        try {
            if (plan.cost() > 0) {
                Transaction tx = Transaction.withdraw(AccountRef.player(plan.profile()), Currency.CRYSTALS, plan.cost(), plan.ledgerReason());
                future = payer.run(decorate(tx, plan, "enchant:" + plan.id()), guard).thenApply(r -> map(r, failure));
            } else if (plan.refund() > 0) {
                Transaction tx = Transaction.deposit(AccountRef.player(plan.profile()), Currency.CRYSTALS, plan.refund(), plan.ledgerReason());
                future = payer.run(decorate(tx, plan, "enchant:" + plan.id()), guard).thenApply(r -> map(r, failure));
            } else {
                future = db.transaction(c -> {
                    if (!guard.holds(c)) {
                        throw new Refused();
                    }
                    return Result.of(Status.OK);
                }).exceptionally(e -> {
                    if (Database.cause(e) instanceof Refused && failure.status != null) {
                        return new Result(failure.status, failure.book, null);
                    }
                    log.log(Level.SEVERE, "Enchants: " + plan.action() + " " + plan.id() + " failed and was rolled back", Database.cause(e));
                    return Result.of(Status.ERROR);
                });
            }
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Enchants: could not start " + plan.action() + " " + plan.id(), e);
            return CompletableFuture.completedFuture(Result.of(Status.ERROR));
        }
        return future.exceptionally(e -> {
            log.log(Level.SEVERE, "Enchants: " + plan.action() + " " + plan.id() + " failed", Database.cause(e));
            return Result.of(Status.ERROR);
        });
    }

    private static Transaction decorate(Transaction tx, OpPlan plan, String key) {
        Transaction out = tx.actor(Actor.player(plan.player(), plan.playerName()))
                .reference(plan.id().toString())
                .idempotencyKey(key)
                .detail("action", plan.action())
                .detail("player", plan.player().toString())
                .detail("target_name", plan.playerName());
        if (plan.toolId() != null) {
            out = out.detail("tool", plan.toolId().toString());
        }
        if (plan.piece() != null) {
            out = out.detail("set_entry", plan.piece().entryId().toString()).detail("piece", plan.piece().piece().key());
        }
        return out;
    }

    private static Result map(TxResult r, Failure failure) {
        return switch (r.status()) {
            case SUCCESS -> new Result(Status.OK, null, r.fromBalance() != null ? r.fromBalance() : r.toBalance());
            case INSUFFICIENT_FUNDS -> Result.of(Status.NOT_ENOUGH);
            case DUPLICATE -> failure.status != null ? new Result(failure.status, failure.book, null) : Result.of(Status.ERROR);
            default -> Result.of(Status.ERROR);
        };
    }

    /** The database part of an operation (inside the transaction). False = roll back, see {@code failure}. */
    private boolean apply(Connection c, OpPlan plan, Failure failure) throws SQLException {
        failure.reset();
        long now = clock.getAsLong();
        if (plan.piece() != null) {
            // Lock the wardrobe row first (fixed order: piece, then books) and check nothing moved since the menu.
            if (pieces == null) {
                throw new SQLException("The wardrobe is not running");
            }
            Optional<List<ToolPerk>> current = pieces.lock(c, plan.profile(), plan.piece().entryId(), plan.piece().piece());
            if (current.isEmpty() || !net.skybornmc.skyblock.common.wardrobe.PieceEnchants.sorted(current.get())
                    .equals(net.skybornmc.skyblock.common.wardrobe.PieceEnchants.sorted(plan.perksBefore()))) {
                failure.set(Status.PIECE_CHANGED, null);
                return false;
            }
        }
        for (OpPlan.Book b : plan.consume()) {
            Optional<BookStore.Row> row = store.lock(c, b.id());
            if (row.isEmpty() || row.get().state() != BookStore.State.ACTIVE || row.get().scroll() != b.scroll()
                    || (!b.scroll() && (!row.get().enchant().equals(b.enchant()) || row.get().level() != b.level()))) {
                failure.set(Status.BOOK_INVALID, b.id());
                return false;
            }
            if (!store.move(c, b.id(), BookStore.State.ACTIVE, BookStore.State.CONSUMED, plan.player(), now, plan.reason())) {
                failure.set(Status.BOOK_INVALID, b.id());
                return false;
            }
        }
        if (plan.daily() != null && !store.useDaily(c, plan.profile(), plan.daily().day(), plan.daily().roll(), plan.daily().limit())) {
            failure.set(Status.LIMIT, null);
            return false;
        }
        for (OpPlan.Book b : plan.mint()) {
            store.insert(c, b.id(), b.scroll(), b.enchant(), b.level(), plan.season(), b.origin(), plan.player(), plan.profile(), now, plan.reason());
        }
        if (plan.toolId() != null) {
            perks.save(c, plan.toolId(), plan.perksAfter());
        }
        if (plan.piece() != null) {
            pieces.save(c, plan.piece().entryId(), plan.piece().piece(), plan.perksAfter());
        }
        for (String enchant : enchants(plan)) {
            store.markSeen(c, plan.player(), plan.season(), enchant, now);
        }
        return true;
    }

    private static Set<String> enchants(OpPlan plan) {
        Set<String> out = new LinkedHashSet<>();
        plan.consume().stream().filter(b -> !b.scroll()).forEach(b -> out.add(b.enchant()));
        plan.mint().stream().filter(b -> !b.scroll()).forEach(b -> out.add(b.enchant()));
        return out;
    }

    // ------------------------------------------------------------------ undo

    /**
     * Undoes the database part of a committed operation whose items could not be changed (the player left, died or
     * the tool moved): consumed books back to active, new books void, the tool's old perks saved, the daily roll given
     * back, and the crystals returned (or a shatter refund taken back). True when it is undone.
     */
    CompletableFuture<Boolean> undo(OpPlan plan) {
        TxGuard guard = c -> {
            long now = clock.getAsLong();
            if (plan.piece() != null) {
                // Checked before any write: undo only if the piece still holds exactly what this operation wrote, else
                // books and crystals would come back while the enchant stays (staff check the audit log instead).
                Optional<List<ToolPerk>> current = pieces == null ? Optional.empty()
                        : pieces.lock(c, plan.profile(), plan.piece().entryId(), plan.piece().piece());
                if (current.isEmpty() || !net.skybornmc.skyblock.common.wardrobe.PieceEnchants.sorted(current.get())
                        .equals(net.skybornmc.skyblock.common.wardrobe.PieceEnchants.sorted(plan.perksAfter()))) {
                    return false;
                }
            }
            for (OpPlan.Book b : plan.consume()) {
                store.reactivate(c, b.id(), plan.reason(), now);
            }
            for (OpPlan.Book b : plan.mint()) {
                store.move(c, b.id(), BookStore.State.ACTIVE, BookStore.State.VOID, plan.player(), now, "undo:" + plan.reason());
            }
            if (plan.daily() != null) {
                store.returnDaily(c, plan.profile(), plan.daily().day(), plan.daily().roll());
            }
            if (plan.toolId() != null) {
                perks.save(c, plan.toolId(), plan.perksBefore());
            }
            if (plan.piece() != null) {
                pieces.save(c, plan.piece().entryId(), plan.piece().piece(), plan.perksBefore());
            }
            return true;
        };
        CompletableFuture<Boolean> future;
        try {
            if (plan.cost() > 0) {
                Transaction tx = Transaction.deposit(AccountRef.player(plan.profile()), Currency.CRYSTALS, plan.cost(), "enchant_undo");
                future = payer.run(decorate(tx, plan, "enchant_undo:" + plan.id()), guard).thenApply(TxResult::ok);
            } else if (plan.refund() > 0) {
                Transaction tx = Transaction.withdraw(AccountRef.player(plan.profile()), Currency.CRYSTALS, plan.refund(), "enchant_undo");
                future = payer.run(decorate(tx, plan, "enchant_undo:" + plan.id()), guard).thenCompose(r -> r.ok()
                        ? CompletableFuture.completedFuture(true)
                        // The refund is already spent: undo the items anyway, the crystals stay (logged by the caller).
                        : db.transaction(guard::holds).thenApply(x -> false));
            } else {
                future = db.transaction(guard::holds);
            }
        } catch (RuntimeException e) {
            future = CompletableFuture.failedFuture(e);
        }
        return future.exceptionally(e -> {
            log.log(Level.SEVERE, "Enchants: undo of " + plan.action() + " " + plan.id() + " failed; staff must check it", Database.cause(e));
            return false;
        });
    }
}

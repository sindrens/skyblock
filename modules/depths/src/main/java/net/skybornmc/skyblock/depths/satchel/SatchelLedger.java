package net.skybornmc.skyblock.depths.satchel;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.TxGuard;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The database side of the satchel (M4.4): every write of one profile runs in order on that profile's chain
 * ({@link SatchelState#chain}), each operation starts on the main thread ({@code main} executor) and its result is
 * handled there too. Dupe safety:
 * <ul>
 *   <li><b>Batched adds</b> ({@link #flush}): the unwritten changes are taken out of the state; a failed write hands
 *       them back, so nothing is lost or written twice.</li>
 *   <li><b>Take-out and sale</b> ({@link #guarded}): the items leave memory first (escrow), then ONE database
 *       transaction applies the unwritten changes, removes the items ({@code amount >= n} or roll back) and, for a sale,
 *       pays the money with the sale record. Only a committed transaction keeps the items out; anything else puts them
 *       back. Items can never be paid twice, taken out twice or vanish.</li>
 * </ul>
 */
final class SatchelLedger {

    /** Thrown inside a transaction to roll it back (the satchel held too few). */
    static final class Refused extends RuntimeException {
        Refused() {
            super("satchel refused", null, false, false);
        }
    }

    private final Database db;
    private final Executor main;
    private final LongSupplier clock;
    private final Logger log;

    SatchelLedger(Database db, Executor main, LongSupplier clock, Logger log) {
        this.db = db;
        this.main = main;
        this.clock = clock;
        this.log = log;
    }

    /**
     * Queues {@code op} on the profile's chain: it starts on the main thread after every earlier operation of that profile
     * finished (failed or not). The returned future completes on the main thread, after {@code inFlight} went down.
     */
    <T> CompletableFuture<T> enqueue(SatchelState state, Supplier<CompletableFuture<T>> op) {
        state.inFlight++;
        CompletableFuture<T> result = state.chain.thenComposeAsync(v -> {
            try {
                return op.get();
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }, main);
        state.chain = result.handle((r, e) -> null);
        CompletableFuture<T> done = new CompletableFuture<>();
        result.whenCompleteAsync((r, e) -> {
            state.inFlight--;
            if (e != null) {
                done.completeExceptionally(e);
            } else {
                done.complete(r);
            }
        }, main);
        return done;
    }

    /** Loads the rows and the upgrade level (after earlier writes). Completes with true on success. */
    CompletableFuture<Boolean> load(SatchelState state) {
        state.loading(true);
        return enqueue(state, () -> db.async(c -> new Object[]{SatchelStore.read(c, state.profile), SatchelStore.upgradeLevel(c, state.profile)}))
                .handle((rows, error) -> {
                    if (error != null) {
                        state.loading(false);
                        log.log(Level.WARNING, "Satchel: could not load " + state.profile, Database.cause(error));
                        return false;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Long> items = (Map<String, Long>) rows[0];
                    state.loaded(items, (Integer) rows[1]);
                    return true;
                });
    }

    /** Writes the unwritten changes (batched drops, Forge use). A failure hands them back for the next try. */
    CompletableFuture<Boolean> flush(SatchelState state) {
        if (!state.hasPending()) {
            return CompletableFuture.completedFuture(true);
        }
        AtomicReference<Map<String, Long>> taken = new AtomicReference<>();
        return enqueue(state, () -> {
            taken.set(state.takePending());
            if (taken.get().isEmpty()) {
                return CompletableFuture.completedFuture(true);
            }
            Map<String, Long> deltas = taken.get();
            long now = clock.getAsLong();
            return db.transaction(c -> {
                if (!SatchelStore.apply(c, state.profile, deltas, now)) {
                    throw new Refused();
                }
                return true;
            });
        }).handle((ok, error) -> {
            if (error != null) {
                if (taken.get() != null) {
                    state.returnPending(taken.get());
                }
                log.log(Level.WARNING, "Satchel: save of " + state.profile + " failed (retried with the next save)", Database.cause(error));
                return false;
            }
            return true;
        });
    }

    /** Take-out: the items are escrowed already; one transaction removes them. True = committed (give the items). */
    CompletableFuture<Boolean> takeOut(SatchelState state, Map<String, Long> escrowed) {
        return guarded(state, escrowed, guard -> db.transaction(c -> {
            if (!guard.holds(c)) {
                throw new Refused();
            }
            return true;
        }), Boolean.TRUE::equals).thenApply(Boolean.TRUE::equals);
    }

    /**
     * Runs a payment (or plain transaction) together with the satchel removal in one database transaction.
     *
     * @param escrowed  items already taken out of memory with {@link SatchelState#escrow}
     * @param payment   gets the guard to run inside its transaction (applies unwritten changes, removes the items)
     * @param committed whether the payment's result means the transaction committed
     * @return the payment's result (on the main thread); when not committed the items are held again already
     */
    <R> CompletableFuture<R> guarded(SatchelState state, Map<String, Long> escrowed, Function<TxGuard, CompletableFuture<R>> payment,
                                     Predicate<R> committed) {
        AtomicReference<Map<String, Long>> taken = new AtomicReference<>();
        return enqueue(state, () -> {
            taken.set(state.takePending());
            Map<String, Long> deltas = taken.get();
            long now = clock.getAsLong();
            TxGuard guard = c -> SatchelStore.apply(c, state.profile, deltas, now) && SatchelStore.take(c, state.profile, escrowed, now);
            return payment.apply(guard);
        }).handle((result, error) -> {
            boolean ok = error == null && result != null && committed.test(result);
            if (!ok) {
                state.release(escrowed);
                if (taken.get() != null) {
                    state.returnPending(taken.get());
                }
                if (error != null && !(Database.cause(error) instanceof Refused)) {
                    log.log(Level.WARNING, "Satchel: take-out/sale of " + state.profile + " failed, items kept", Database.cause(error));
                }
            }
            if (error != null) {
                return null;
            }
            return result;
        });
    }

    /** Buys an upgrade: the payment's guard raises the level from {@code from} to {@code to} in the same transaction. */
    <R> CompletableFuture<R> upgrade(SatchelState state, int from, int to, Function<TxGuard, CompletableFuture<R>> payment) {
        long now = clock.getAsLong();
        return enqueue(state, () -> payment.apply(c -> SatchelStore.setUpgrade(c, state.profile, from, to, now)));
    }

    /** Removes a deleted profile's rows (after its queued work). */
    CompletableFuture<Void> delete(SatchelState state) {
        return enqueue(state, () -> db.run(c -> SatchelStore.deleteProfile(c, state.profile)));
    }

    /** Shutdown: writes the unwritten changes right now on this thread (no chain, no main thread). */
    void flushBlocking(SatchelState state) {
        Map<String, Long> deltas = state.takePending();
        if (deltas.isEmpty()) {
            return;
        }
        try {
            long now = clock.getAsLong();
            db.blockingTransaction(c -> {
                if (!SatchelStore.apply(c, state.profile, deltas, now)) {
                    throw new Refused();
                }
                return null;
            });
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Satchel: final save of " + state.profile + " failed: " + deltas, Database.cause(e));
        }
    }
}

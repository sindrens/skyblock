package net.skybornmc.skyblock.trade;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditLog;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The dupe-safe part of a trade, without Bukkit: escrow in/out, the atomic swap, cancel and crash recovery, plus the
 * audit rows. Futures complete off the main thread.
 *
 * <p><b>The swap</b> is one {@link EconomyService#executeBatch} call: every money leg (one ledger row each, idempotency key
 * {@code trade:<id>:<a|b>:<currency>}) and the item escrow switch ({@link TradeStore#completeGuard}) commit together or
 * not at all. Items are delivered afterwards from the pending rows, each exactly once.</p>
 */
final class TradeEngine {

    static final String AUDIT_COMPLETE = "trade_complete";
    static final String AUDIT_CANCEL = "trade_cancel";
    static final String AUDIT_START = "trade_start";
    static final String AUDIT_DELIVER = "trade_deliver";
    static final String AUDIT_RECOVER = "trade_recover";

    /** P4: answers true (= refuse) when the two profile keys belong to the same player (audited by the caller's check). */
    @FunctionalInterface
    interface OwnProfileCheck {
        boolean blocked(UUID profileA, UUID profileB, Actor actor);
    }

    /** Result of {@link #complete}. */
    enum Outcome {
        /** Money moved and items switched owner (all pending for the receivers). */
        COMPLETED,
        /** One side has too little money now: nothing changed, the trade stays open. */
        INSUFFICIENT,
        /** A balance would overflow: nothing changed, the trade stays open. */
        OVERFLOW,
        /** The trade was not open any more or its items differ from the menu: nothing changed, cancel it. */
        STATE_CHANGED,
        /** Both sides are profiles of the same player (P4): nothing changed, cancel it. */
        OWN_PROFILE,
        /** Database error: nothing changed (rolled back), cancel it. */
        ERROR
    }

    /**
     * @param shortSide the player whose money was short (INSUFFICIENT/OVERFLOW), or null
     */
    record CompleteResult(Outcome outcome, UUID shortSide) {
    }

    private final TradeStore store;
    private final Supplier<EconomyService> economy;
    private final AuditLog audit;
    private final LongSupplier clock;
    private final Logger log;
    private OwnProfileCheck ownProfileCheck = (a, b, actor) -> false;

    TradeEngine(TradeStore store, Supplier<EconomyService> economy, AuditLog audit, LongSupplier clock, Logger log) {
        this.store = store;
        this.economy = economy;
        this.audit = audit;
        this.clock = clock;
        this.log = log;
    }

    void ownProfileCheck(OwnProfileCheck check) {
        this.ownProfileCheck = check;
    }

    TradeStore store() {
        return store;
    }

    // ------------------------------------------------------------------ start

    /** Opens the session row; completes with the new trade id. */
    CompletableFuture<Long> open(TradeSession.Side a, TradeSession.Side b) {
        return store.create(a, b, clock.getAsLong()).thenApply(id -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("trade_id", id);
            d.put("player_b", b.player.toString());
            d.put("profile_a", a.profile.toString());
            d.put("profile_b", b.profile.toString());
            audit.logPlayer(AuditCategory.TRADE, AUDIT_START, Actor.player(a.player, a.name), b.player, b.name, null, null, d);
            return id;
        });
    }

    // ------------------------------------------------------------------ escrow

    /**
     * Stores an offered item; empty if the trade is no longer open (give the item back). {@code token} is a random id
     * that tells after a database error whether the row was written ({@link TradeStore#byToken}).
     */
    CompletableFuture<Optional<Long>> escrow(long tradeId, UUID player, UUID profile, Offer offer, String token) {
        return store.escrow(tradeId, player, profile, offer, token, clock.getAsLong());
    }

    /** Releases an offered item back to its owner (pending); false if the trade is not open. */
    CompletableFuture<Boolean> release(long tradeId, long rowId, UUID profile) {
        return store.release(tradeId, rowId, profile, clock.getAsLong());
    }

    // ------------------------------------------------------------------ the swap

    /** The money legs of a swap, A's first, in currency order. */
    static List<Transaction> legs(TradeSession.Snapshot s) {
        List<Transaction> legs = new ArrayList<>();
        addLegs(legs, s, true);
        addLegs(legs, s, false);
        return legs;
    }

    private static void addLegs(List<Transaction> out, TradeSession.Snapshot s, boolean fromA) {
        Map<Currency, Long> money = fromA ? s.moneyA() : s.moneyB();
        for (Currency currency : Currency.values()) {
            long amount = money.getOrDefault(currency, 0L);
            if (amount <= 0) {
                continue;
            }
            // Crystals and earned credits can't be offered; if one ever got into a snapshot its leg stays in the batch and
            // Transaction#problem refuses the whole swap (INVALID: nothing moves), it is never silently dropped.
            UUID from = fromA ? s.profileA() : s.profileB();
            UUID to = fromA ? s.profileB() : s.profileA();
            out.add(Transaction.transfer(AccountRef.player(from), AccountRef.player(to), currency, amount, TxReason.TRADE)
                    .reference("trade:" + s.id())
                    .idempotencyKey(idempotencyKey(s.id(), fromA, currency))
                    .actor(fromA ? Actor.player(s.playerA(), s.nameA()) : Actor.player(s.playerB(), s.nameB()))
                    .detail("target_name", fromA ? s.nameB() : s.nameA())
                    .detail("trade_id", s.id()));
        }
    }

    static String idempotencyKey(long tradeId, boolean sideA, Currency currency) {
        return "trade:" + tradeId + ":" + (sideA ? "a" : "b") + ":" + currency.key();
    }

    /**
     * Runs the swap: P4 check, then the money legs and the escrow switch in one database transaction. Audits the
     * completed trade with both item lists and both money offers.
     */
    CompletableFuture<CompleteResult> complete(TradeSession.Snapshot s, Actor actor) {
        if (s.profileA().equals(s.profileB()) || ownProfileCheck.blocked(s.profileA(), s.profileB(), actor)) {
            return CompletableFuture.completedFuture(new CompleteResult(Outcome.OWN_PROFILE, null));
        }
        List<Transaction> legs = legs(s);
        CompletableFuture<BatchResult> run;
        try {
            run = economy.get().executeBatch(legs, c -> TradeStore.completeGuard(c, s, clock.getAsLong()));
        } catch (RuntimeException e) {
            run = CompletableFuture.failedFuture(e);
        }
        return run.handle((r, error) -> {
            if (error != null || r == null) {
                log.log(Level.SEVERE, "Trade " + s.id() + ": the swap failed (rolled back)", error == null ? null : Database.cause(error));
                return new CompleteResult(Outcome.ERROR, null);
            }
            if (r.ok()) {
                auditComplete(s, r);
                return new CompleteResult(Outcome.COMPLETED, null);
            }
            UUID shortSide = r.failedIndex() >= 0 && r.failedIndex() < legs.size() ? payer(s, legs.get(r.failedIndex())) : null;
            return switch (r.status()) {
                case INSUFFICIENT_FUNDS -> new CompleteResult(Outcome.INSUFFICIENT, shortSide);
                case OVERFLOW -> new CompleteResult(Outcome.OVERFLOW, shortSide);
                case DUPLICATE -> new CompleteResult(Outcome.STATE_CHANGED, null);
                default -> new CompleteResult(Outcome.ERROR, null);
            };
        });
    }

    private static UUID payer(TradeSession.Snapshot s, Transaction leg) {
        return leg.from() != null && leg.from().owner().equals(s.profileA()) ? s.playerA() : s.playerB();
    }

    private void auditComplete(TradeSession.Snapshot s, BatchResult r) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("trade_id", s.id());
        d.put("player_a", s.playerA().toString());
        d.put("name_a", s.nameA());
        d.put("profile_a", s.profileA().toString());
        d.put("player_b", s.playerB().toString());
        d.put("name_b", s.nameB());
        d.put("profile_b", s.profileB().toString());
        d.put("items_a", s.itemsA().stream().map(Offer::audit).toList());
        d.put("items_b", s.itemsB().stream().map(Offer::audit).toList());
        d.put("money_a", moneyKeys(s.moneyA()));
        d.put("money_b", moneyKeys(s.moneyB()));
        List<Long> ledger = new ArrayList<>();
        for (TxResult t : r.results()) {
            ledger.add(t.ledgerId());
        }
        d.put("ledger_ids", ledger);
        long money = s.moneyA().getOrDefault(Currency.MONEY, 0L) + s.moneyB().getOrDefault(Currency.MONEY, 0L);
        audit.logPlayer(AuditCategory.TRADE, AUDIT_COMPLETE, Actor.player(s.playerA(), s.nameA()), s.playerB(), s.nameB(),
                money > 0 ? money : null, null, d);
    }

    private static Map<String, Long> moneyKeys(Map<Currency, Long> money) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Currency c : Currency.values()) {
            Long v = money.get(c);
            if (v != null && v > 0) {
                out.put(c.key(), v);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ cancel and recovery

    /**
     * Cancels a trade in the database (every held item goes back to whoever put it in, as pending) and audits it with
     * the returned items. If the trade turns out to be completed already, nothing changes ({@code previous}).
     */
    CompletableFuture<TradeStore.CancelOutcome> cancel(long tradeId, String reason, Actor actor, UUID playerA, String nameA,
                                                       UUID playerB, String nameB) {
        return store.cancel(tradeId, reason, clock.getAsLong()).thenApply(outcome -> {
            if (TradeStore.OPEN.equals(outcome.previous())) {
                auditCancel(tradeId, reason, actor, playerA, nameA, playerB, nameB, outcome.returned());
            }
            return outcome;
        });
    }

    private void auditCancel(long tradeId, String reason, Actor actor, UUID playerA, String nameA, UUID playerB, String nameB,
                             List<TradeStore.EscrowRow> returned) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("trade_id", tradeId);
        d.put("reason", reason);
        if (playerA != null) {
            d.put("player_a", playerA.toString());
            d.put("name_a", nameA);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (TradeStore.EscrowRow row : returned) {
            Map<String, Object> m = new LinkedHashMap<>(row.offer().audit());
            m.put("returned_to", row.ownerPlayer().toString());
            items.add(m);
        }
        d.put("returned", items);
        audit.log(AuditCategory.TRADE, AUDIT_CANCEL, actor, playerB == null ? null : playerB.toString(), nameB, null, null, d);
    }

    /**
     * Startup: every trade still open in the database (server crash or hard stop) is cancelled, so all of its items go
     * back to their owners. Only trades created before {@code createdBefore} (the module start), so a trade started
     * while the recovery runs is never touched. Completes with the number of trades recovered.
     */
    CompletableFuture<Integer> recover(long createdBefore) {
        return store.openTradesBefore(createdBefore).thenCompose(ids -> {
            CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
            for (long id : ids) {
                chain = chain.thenCompose(n -> store.cancel(id, "restart", clock.getAsLong()).thenApply(outcome -> {
                    if (TradeStore.OPEN.equals(outcome.previous())) {
                        auditCancel(id, "restart", Actor.system("trade-recovery"), null, null, null, null, outcome.returned());
                        return n + 1;
                    }
                    return n;
                }));
            }
            return chain;
        }).thenApply(n -> {
            if (n > 0) {
                log.warning("Trade: " + n + " trade(s) were still open after a restart; their items went back to the owners.");
                audit.log(AuditCategory.TRADE, AUDIT_RECOVER, Actor.system("trade-recovery"), Map.of("trades", n));
            }
            return n;
        });
    }

    /**
     * While running: cancels trades that are open in the database but not in memory (a cancel that failed on a database
     * error), so their items go back. {@code active} holds the ids of trades that are really open.
     */
    CompletableFuture<List<TradeStore.CancelOutcome>> cancelOrphans(java.util.Set<Long> active, long olderThanMillis) {
        return store.openTradesBefore(clock.getAsLong() - olderThanMillis).thenCompose(ids -> {
            CompletableFuture<List<TradeStore.CancelOutcome>> chain = CompletableFuture.completedFuture(new ArrayList<>());
            for (long id : ids) {
                if (active.contains(id)) {
                    continue;
                }
                chain = chain.thenCompose(list -> store.cancel(id, "orphan", clock.getAsLong()).thenApply(outcome -> {
                    if (TradeStore.OPEN.equals(outcome.previous())) {
                        auditCancel(id, "orphan", Actor.system("trade-recovery"), null, null, null, null, outcome.returned());
                        list.add(outcome);
                    }
                    return list;
                }));
            }
            return chain;
        });
    }

    /** Shutdown: cancels a trade right now on this thread (items wait as pending for their owners' next join). */
    TradeStore.CancelOutcome cancelBlocking(long tradeId, String reason, Actor actor, UUID playerA, String nameA, UUID playerB,
                                            String nameB) {
        TradeStore.CancelOutcome outcome = store.cancelBlocking(tradeId, reason, clock.getAsLong());
        if (TradeStore.OPEN.equals(outcome.previous())) {
            auditCancel(tradeId, reason, actor, playerA, nameA, playerB, nameB, outcome.returned());
        }
        return outcome;
    }

    // ------------------------------------------------------------------ delivery

    /** Takes one pending row for delivery (exactly once) and audits it. */
    CompletableFuture<Boolean> claim(TradeStore.EscrowRow row, Actor actor) {
        return store.claim(row.id(), row.ownerProfile(), clock.getAsLong()).thenApply(ok -> {
            if (ok) {
                Map<String, Object> d = new LinkedHashMap<>(row.offer().audit());
                d.put("trade_id", row.tradeId());
                d.put("returned", row.originProfile().equals(row.ownerProfile()));
                audit.logPlayer(AuditCategory.TRADE, AUDIT_DELIVER, actor, row.ownerPlayer(), null, (long) row.offer().amount(), null, d);
            }
            return ok;
        });
    }
}

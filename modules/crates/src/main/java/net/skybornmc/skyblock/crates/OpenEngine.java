package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The atomic part of a crate open (no Bukkit, tested on H2): <b>one</b> database transaction takes the keys (bound
 * first), moves the pity counter, writes one {@code crate_opens} row per open, adds won keys and pays money, crystals and
 * shards through the ledger (reason {@code crate_reward}, idempotency key {@code crate_open:<batch>:<currency>}). All of
 * it commits or none of it does. Items, cosmetics and fuel are handed out after the commit from the rows marked
 * {@code pending}; a crash in between can lose them (staff review), never duplicate them.
 */
final class OpenEngine {

    /** How a commit ended. */
    enum Status {
        OK,
        /** Keys or pity changed since they were read (another open, a staff change): nothing happened, read and try again. */
        CHANGED,
        /** Database or economy error: nothing happened. */
        ERROR
    }

    /** Runs transactions and a guard in one database transaction (the economy's {@code executeBatch}). */
    @FunctionalInterface
    interface Runner {
        CompletableFuture<BatchResult> run(List<Transaction> transactions, TxGuard guard);
    }

    private OpenEngine() {
    }

    /** The ledger deposits of a batch: one per currency with an amount above 0. */
    static List<Transaction> transactions(OpenPlan.Plan plan, UUID profile, Actor actor) {
        List<Transaction> out = new ArrayList<>();
        for (Currency currency : List.of(Currency.MONEY, Currency.CRYSTALS, Currency.SHARDS)) {
            long total = plan.total(currency);
            if (total <= 0) {
                continue;
            }
            out.add(Transaction.deposit(AccountRef.player(profile), currency, total, TxReason.CRATE_REWARD)
                    .actor(actor)
                    .reference("crate:" + plan.batchId())
                    .idempotencyKey("crate_open:" + plan.batchId() + ":" + currency.key())
                    .detail("crate", plan.crate().id())
                    .detail("opens", plan.grants().size())
                    .detail("batch", plan.batchId().toString())
                    .detail("profile", profile.toString()));
        }
        return out;
    }

    /** The open rows of a batch: paid grants {@code done}, hand-outs {@code pending}. */
    static List<CrateStore.OpenRow> rows(OpenPlan.Plan plan, UUID profile, UUID player, int season, long now) {
        List<CrateStore.OpenRow> rows = new ArrayList<>(plan.grants().size());
        for (OpenPlan.Grant g : plan.grants()) {
            rows.add(new CrateStore.OpenRow(g.openId(), plan.batchId(), profile, player, plan.crate().id(), g.reward().id(), g.kind(),
                    cut(g.ref(), 64), g.amount(), g.top(), g.forced(), g.bound(),
                    g.inTransaction() ? CrateStore.DONE : CrateStore.PENDING, season, now));
        }
        return rows;
    }

    /** The SQL of the transaction. False (everything rolls back) when the keys or the pity counter changed meanwhile. */
    static TxGuard guard(OpenPlan.Plan plan, UUID profile, UUID player, Optional<CrateStore.Pity> before, int season, long now) {
        return c -> {
            if (!CrateStore.take(c, profile, plan.crate().id(), plan.keys().bound(), plan.keys().tradeable(), now)) {
                return false;
            }
            if (!CrateStore.setPity(c, profile, plan.crate().id(), before, plan.pityAfter(), season, now)) {
                return false;
            }
            CrateStore.insertOpens(c, rows(plan, profile, player, season, now));
            for (OpenPlan.KeyGrant k : plan.keyGrants()) {
                CrateStore.addKeys(c, profile, k.crate(), k.bound(), k.tradeable(), now);
            }
            return true;
        };
    }

    /** Commits a planned batch. Never completes exceptionally. */
    static CompletableFuture<Status> commit(Runner runner, OpenPlan.Plan plan, UUID profile, UUID player,
                                            Optional<CrateStore.Pity> before, int season, Actor actor, long now) {
        if (plan.grants().isEmpty() || plan.keys().total() != plan.grants().size()) {
            return CompletableFuture.completedFuture(Status.ERROR);
        }
        CompletableFuture<BatchResult> run;
        try {
            run = runner.run(transactions(plan, profile, actor), guard(plan, profile, player, before, season, now));
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(Status.ERROR);
        }
        return run.handle((r, error) -> {
            if (error != null || r == null) {
                return Status.ERROR;
            }
            if (r.ok()) {
                return Status.OK;
            }
            return r.status() == TxResult.Status.DUPLICATE && r.failedIndex() < 0 ? Status.CHANGED : Status.ERROR;
        });
    }

    static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}

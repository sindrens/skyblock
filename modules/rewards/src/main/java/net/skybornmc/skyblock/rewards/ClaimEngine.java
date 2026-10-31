package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxResult;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The atomic part of a claim (no Bukkit, tested on H2). <b>One</b> database transaction checks and moves the calendar
 * (or the playtime milestones), writes the claim row, pays money, crystals and earned credits through the ledger (idempotency key
 * per claim and currency) and writes the hand-outs as {@code pending} grant rows. All of it commits or none of it does,
 * so a claim can never pay twice; hand-outs happen after the commit ({@link Grants}).
 */
final class ClaimEngine {

    /** How a commit ended. */
    enum Status {
        OK,
        /** The calendar or milestones changed since they were read (another claim): nothing happened. */
        CHANGED,
        /** Database or economy error: nothing happened. */
        ERROR
    }

    /** Runs ledger transactions and a guard in one database transaction. */
    @FunctionalInterface
    interface Runner {
        CompletableFuture<BatchResult> run(List<Transaction> transactions, TxGuard guard);
    }

    /** Thrown inside a local transaction when the guard does not hold: rolls everything back. */
    static final class GuardFailed extends SQLException {
        GuardFailed() {
            super("reward guard failed");
        }
    }

    private ClaimEngine() {
    }

    /**
     * The runner of the server: with currencies the economy's {@code executeBatch} (ledger + guard in one transaction),
     * without currencies a plain database transaction that runs the guard.
     */
    static Runner runner(Database database, Optional<EconomyService> economy) {
        return (transactions, guard) -> {
            if (transactions.isEmpty()) {
                return local(database, guard);
            }
            return economy.map(e -> e.executeBatch(transactions, guard))
                    .orElseGet(() -> CompletableFuture.completedFuture(BatchResult.failed(TxResult.Status.ERROR, -1)));
        };
    }

    /** Runs only the guard in its own transaction (claims without currencies). */
    static CompletableFuture<BatchResult> local(Database database, TxGuard guard) {
        return database.transaction(c -> {
            if (!guard.holds(c)) {
                throw new GuardFailed();
            }
            return BatchResult.success(List.of());
        }).handle((r, error) -> {
            if (error == null) {
                return r;
            }
            return Database.cause(error) instanceof GuardFailed ? BatchResult.failed(TxResult.Status.DUPLICATE, -1)
                    : BatchResult.failed(TxResult.Status.ERROR, -1);
        });
    }

    /**
     * Ledger deposits, one per currency above 0, to the profile ({@code AccountRef.player(profile)}; credits move to the
     * owner automatically). Idempotency key {@code <key>:<currency>}: the same claim can never be paid twice.
     */
    static List<Transaction> transactions(Payout payout, UUID profile, String reason, String key, Actor actor, Map<String, Object> details) {
        List<Transaction> out = new ArrayList<>();
        for (Currency currency : List.of(Currency.MONEY, Currency.CRYSTALS, Currency.CREDITS_EARNED, Currency.SHARDS)) {
            long total = payout.total(currency);
            if (total <= 0) {
                continue;
            }
            Transaction t = Transaction.deposit(AccountRef.player(profile), currency, total, reason)
                    .actor(actor)
                    .reference(cut(key, 64))
                    .idempotencyKey(cut(key + ":" + currency.key(), 96))
                    .detail("profile", profile.toString());
            for (Map.Entry<String, Object> d : details.entrySet()) {
                t = t.detail(d.getKey(), d.getValue());
            }
            out.add(t);
        }
        return out;
    }

    /** The grant rows of a payout, all {@code pending}. */
    static List<RewardStore.GrantRow> grantRows(Payout payout, UUID profile, UUID player, String source, String sourceRef, long now) {
        List<RewardStore.GrantRow> rows = new ArrayList<>();
        for (Payout.Grant g : payout.grants()) {
            rows.add(new RewardStore.GrantRow(UUID.randomUUID(), profile, player, source, cut(sourceRef, 64), g.kind(), cut(g.ref(), 64),
                    g.amount(), RewardStore.PENDING, now));
        }
        return rows;
    }

    /** The SQL of a daily claim. False (everything rolls back) when the calendar changed since it was read. */
    static TxGuard dailyGuard(UUID profile, UUID player, Optional<DailyRules.State> before, DailyRules.Claim claim,
                              List<RewardStore.GrantRow> grants, long now) {
        return c -> {
            if (!RewardStore.saveCalendar(c, profile, before, claim.after(), now)) {
                return false;
            }
            RewardStore.insertClaim(c, profile, claim.after().lastClaimDate(), claim.day(), claim.streakAfter(), player, now);
            RewardStore.insertGrants(c, grants);
            return true;
        };
    }

    /** The SQL of a playtime payout. False when the paid milestones changed since they were read. */
    static TxGuard playtimeGuard(UUID profile, PlaytimeRules.Row before, PlaytimeRules.Row after, List<RewardStore.GrantRow> grants,
                                 long now) {
        return c -> {
            if (!RewardStore.payMilestones(c, profile, before, after, now)) {
                return false;
            }
            RewardStore.insertGrants(c, grants);
            return true;
        };
    }

    /** Commits. Never completes exceptionally. */
    static CompletableFuture<Status> commit(Runner runner, List<Transaction> transactions, TxGuard guard) {
        CompletableFuture<BatchResult> run;
        try {
            run = runner.run(transactions, guard);
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

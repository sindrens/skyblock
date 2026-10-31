package net.skybornmc.skyblock.quests;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The atomic part of a quest payout (no Bukkit, tested on H2). <b>One</b> database transaction marks the quest done
 * (only if it is still open), pays money and crystals through the ledger (idempotency key per quest instance and
 * currency), claims the all-done bonus of the set once and writes keys and pass XP as {@code pending} grant rows. All of it
 * commits or none of it does, so a quest pays exactly once, even after a crash or a second attempt.
 */
final class Payouts {

    enum Status {
        OK,
        /** The quest was finished or swapped already: nothing happened. */
        CHANGED,
        /** Database or economy error: nothing happened (tried again later). */
        ERROR
    }

    /** Runs ledger transactions and a guard in one database transaction. */
    @FunctionalInterface
    interface Runner {
        CompletableFuture<BatchResult> run(List<Transaction> transactions, TxGuard guard);
    }

    /** The all-done bonus of a set: {@code amount} keys of {@code crate}, given with key source {@code reason}. */
    record Bonus(String crate, int amount, String reason) {
    }

    /** What the guard did (filled inside the transaction; read after the commit). */
    static final class Outcome {
        final List<QuestStore.GrantRow> grants = new ArrayList<>();
        final List<UUID> paidHelpers = new ArrayList<>();
        boolean bonus;

        void reset() {
            grants.clear();
            paidHelpers.clear();
            bonus = false;
        }
    }

    /** A prepared payout: run it with {@link #commit}. */
    record Plan(List<Transaction> transactions, TxGuard guard, Outcome outcome) {
    }

    /** Thrown inside a local transaction when the guard does not hold: rolls everything back. */
    static final class GuardFailed extends SQLException {
        GuardFailed() {
            super("quest guard failed");
        }
    }

    private Payouts() {
    }

    /** The server's runner: the economy's {@code executeBatch} with currencies, a plain transaction without. */
    static Runner runner(Database database, Optional<EconomyService> economy) {
        return (transactions, guard) -> {
            if (transactions.isEmpty()) {
                return local(database, guard);
            }
            return economy.map(e -> e.executeBatch(transactions, guard))
                    .orElseGet(() -> CompletableFuture.completedFuture(BatchResult.failed(TxResult.Status.ERROR, -1)));
        };
    }

    /** Runs only the guard in its own transaction (payouts without currencies). */
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
     * A daily or weekly quest of one profile.
     *
     * @param bonus  the set's all-done bonus (null = none)
     * @param passXp true while the battle pass takes XP (otherwise no pass XP row is written)
     */
    static Plan personal(QuestSet set, Quest quest, UUID player, Bonus bonus, boolean passXp, Actor actor, long now) {
        UUID profile = set.owner;
        Outcome outcome = new Outcome();
        List<Transaction> txs = new ArrayList<>();
        Map<String, Object> details = details(set, quest);
        String key = "quest:" + hex(quest.uid);
        deposit(txs, profile, Currency.MONEY, quest.rewardMoney, key, actor, details);
        deposit(txs, profile, Currency.CRYSTALS, quest.rewardCrystals, key, actor, details);
        TxGuard guard = c -> {
            outcome.reset();
            if (!QuestStore.lockSet(c, set.kind, set.owner, set.period) || !QuestStore.markDone(c, quest.uid, now)) {
                return false;
            }
            List<QuestStore.GrantRow> rows = new ArrayList<>();
            if (passXp && quest.rewardXp > 0) {
                rows.add(grant(profile, player, key, QuestStore.PASS_XP, set.kind.id(), quest.rewardXp, now));
            }
            if (bonus != null && bonus.amount() > 0 && QuestStore.openCount(c, set.kind, set.owner, set.period) == 0
                    && QuestStore.claimBonus(c, set.kind, set.owner, set.period)) {
                rows.add(grant(profile, player, "quest_bonus:" + set.kind.id() + ":" + set.period, QuestStore.KEY,
                        bonus.crate() + ":" + bonus.reason(), bonus.amount(), now));
                outcome.bonus = true;
            }
            QuestStore.insertGrants(c, rows);
            outcome.grants.addAll(rows);
            return true;
        };
        return new Plan(txs, guard, outcome);
    }

    /**
     * An island mission: every helper (profile → player) gets the crystals and pass XP; {@code contributions} are stored
     * in the same transaction so the helper list in /logs matches what was paid.
     */
    static Plan island(QuestSet set, Quest quest, Map<UUID, UUID> helpers, Map<UUID, Long> contributions, boolean passXp,
                       Actor actor, long now) {
        Outcome outcome = new Outcome();
        List<Transaction> txs = new ArrayList<>();
        Map<String, Object> details = details(set, quest);
        details.put("island", set.owner.toString());
        for (UUID profile : helpers.keySet()) {
            deposit(txs, profile, Currency.CRYSTALS, quest.rewardCrystals, "qisl:" + hex(quest.uid) + ":" + hex(profile), actor, details);
        }
        TxGuard guard = c -> {
            outcome.reset();
            if (!QuestStore.markDone(c, quest.uid, now)) {
                return false;
            }
            for (Map.Entry<UUID, Long> e : contributions.entrySet()) {
                UUID player = helpers.getOrDefault(e.getKey(), set.helperPlayers.getOrDefault(e.getKey(), e.getKey()));
                QuestStore.saveContribution(c, quest.uid, e.getKey(), player, e.getValue());
            }
            List<QuestStore.GrantRow> rows = new ArrayList<>();
            for (Map.Entry<UUID, UUID> h : helpers.entrySet()) {
                if (!QuestStore.markHelperPaid(c, quest.uid, h.getKey(), now)) {
                    return false;
                }
                outcome.paidHelpers.add(h.getKey());
                if (passXp && quest.rewardXp > 0) {
                    rows.add(grant(h.getKey(), h.getValue(), "qisl:" + hex(quest.uid), QuestStore.PASS_XP, set.kind.id(), quest.rewardXp, now));
                }
            }
            QuestStore.insertGrants(c, rows);
            outcome.grants.addAll(rows);
            return true;
        };
        return new Plan(txs, guard, outcome);
    }

    /** Commits. Never completes exceptionally. */
    static CompletableFuture<Status> commit(Runner runner, Plan plan) {
        CompletableFuture<BatchResult> run;
        try {
            run = runner.run(plan.transactions(), plan.guard());
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

    private static void deposit(List<Transaction> txs, UUID profile, Currency currency, long amount, String key, Actor actor,
                                Map<String, Object> details) {
        if (amount <= 0) {
            return;
        }
        Transaction t = Transaction.deposit(AccountRef.player(profile), currency, amount, TxReason.QUEST_REWARD)
                .actor(actor)
                .reference(cut(key, 64))
                .idempotencyKey(cut(key + ":" + currency.key(), 96))
                .detail("profile", profile.toString());
        for (Map.Entry<String, Object> d : details.entrySet()) {
            t = t.detail(d.getKey(), d.getValue());
        }
        txs.add(t);
    }

    private static Map<String, Object> details(QuestSet set, Quest quest) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("kind", set.kind.id());
        d.put("type", quest.type.id());
        d.put("target", quest.target);
        d.put("period", set.period);
        d.put("quest", quest.uid.toString());
        return d;
    }

    private static QuestStore.GrantRow grant(UUID profile, UUID player, String sourceRef, String kind, String ref, long amount, long now) {
        return new QuestStore.GrantRow(UUID.randomUUID(), profile, player, cut(sourceRef, 64), kind, cut(ref, 32), amount,
                QuestStore.PENDING, now);
    }

    static String hex(UUID id) {
        return id.toString().replace("-", "");
    }

    static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}

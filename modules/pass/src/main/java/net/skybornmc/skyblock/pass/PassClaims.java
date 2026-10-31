package net.skybornmc.skyblock.pass;

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

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The atomic part of a claim (no Bukkit, tested on H2). <b>One</b> database transaction locks the profile's XP row, checks
 * that every tier is reached (and premium for premium tiers), writes the claim rows (primary key = once per tier), pays
 * money and crystals through the ledger (reason {@code pass_reward}, idempotency key per claim) and writes keys, boosters
 * and cosmetics as {@code pending} hand-out rows. All of it commits or nothing does.
 */
final class PassClaims {

    static final String KEY_REASON = "pass";
    static final String OVERFLOW_KEY_REASON = "pass_overflow";

    enum Status {
        OK,
        /** A tier was claimed already, not reached, or premium is missing: nothing happened. */
        CHANGED,
        /** Database or economy error: nothing happened. */
        ERROR
    }

    /** Runs ledger transactions and a guard in one database transaction. */
    @FunctionalInterface
    interface Runner {
        CompletableFuture<BatchResult> run(List<Transaction> transactions, TxGuard guard);
    }

    /** What a claim gives (for the chat line and the audit). */
    record Summary(long money, long crystals, Map<String, Long> keys, int boosters, List<PassTable.Reward> cosmetics) {
    }

    /** A prepared claim: run it with {@link #commit}; {@code grants} are handed out after the commit. */
    record Plan(List<Transaction> transactions, TxGuard guard, List<PassStore.GrantRow> grants, Summary summary,
                List<PassRules.TierRef> tiers) {
    }

    /** Thrown inside a local transaction when the guard does not hold: rolls everything back. */
    static final class GuardFailed extends SQLException {
        GuardFailed() {
            super("pass claim guard failed");
        }
    }

    /**
     * Everything the plan needs about the claimer.
     *
     * @param stageTier the player's stage tier now (money and crystal pouches scale with it)
     * @param premium   the account has the premium pass this season (checked on the main thread)
     */
    record Claimer(UUID profile, UUID player, String season, int stageTier, boolean premium) {
    }

    private PassClaims() {
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

    /** Builds the claim of {@code tiers} (from {@link PassRules#claimable}). */
    static Plan plan(Claimer who, List<PassRules.TierRef> tiers, PassTable table, PassMath.Curve curve, long[] refPerMinute,
                     double crystalGrowth, Actor actor, long now) {
        long money = 0;
        long crystals = 0;
        Map<String, long[]> keyRows = new LinkedHashMap<>(); // crate:reason -> {amount, first tier}
        Map<String, Long> keys = new LinkedHashMap<>();
        List<PassStore.GrantRow> grants = new ArrayList<>();
        List<PassTable.Reward> cosmetics = new ArrayList<>();
        int boosters = 0;
        List<Integer> free = new ArrayList<>();
        List<Integer> premium = new ArrayList<>();
        for (PassRules.TierRef ref : tiers) {
            (ref.track() == PassTable.Track.FREE ? free : premium).add(ref.tier());
            boolean overflow = ref.track() == PassTable.Track.FREE && ref.tier() > curve.max();
            for (PassTable.Reward r : table.rewards(ref.track(), ref.tier(), curve.max())) {
                if (ref.track() == PassTable.Track.PREMIUM && !r.kind().cosmetic()) {
                    continue; // never on premium (R3-44); the parser already refuses these
                }
                switch (r.kind()) {
                    case MONEY -> money += PassMath.money(r.minutes(), who.stageTier(), refPerMinute);
                    case CRYSTALS -> crystals += PassMath.crystals(r.amount(), who.stageTier(), crystalGrowth);
                    case KEY -> {
                        String reason = overflow ? OVERFLOW_KEY_REASON : KEY_REASON;
                        keyRows.computeIfAbsent(r.ref() + ":" + reason, k -> new long[]{0, ref.tier()})[0] += r.amount();
                        keys.merge(r.ref(), r.amount(), Long::sum);
                    }
                    case BOOSTER -> {
                        grants.add(grant(who, ref.tier(), PassStore.BOOSTER, r.ref(), 1, now));
                        boosters++;
                    }
                    case TITLE, TAG -> {
                        grants.add(grant(who, ref.tier(), r.kind() == PassTable.Kind.TITLE ? PassStore.TITLE : PassStore.TAG, r.ref(), 1, now));
                        cosmetics.add(r);
                    }
                }
            }
        }
        for (Map.Entry<String, long[]> e : keyRows.entrySet()) {
            grants.add(0, grant(who, (int) e.getValue()[1], PassStore.KEY, e.getKey(), e.getValue()[0], now));
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("season", who.season());
        details.put("profile", who.profile().toString());
        details.put("free", free.toString());
        details.put("premium", premium.toString());
        String key = "pass:" + hex(UUID.nameUUIDFromBytes((who.season() + "|" + who.profile() + "|" + who.player() + "|" + free + "|" + premium)
                .getBytes(StandardCharsets.UTF_8)));
        List<Transaction> txs = new ArrayList<>();
        deposit(txs, who.profile(), Currency.MONEY, money, key, actor, details);
        deposit(txs, who.profile(), Currency.CRYSTALS, crystals, key, actor, details);
        List<PassStore.GrantRow> rows = List.copyOf(grants);
        TxGuard guard = c -> {
            long xp = PassStore.lockXp(c, who.profile(), who.season());
            for (int t : free) {
                if (curve.tier(xp) < t) {
                    return false;
                }
            }
            for (int t : premium) {
                if (!who.premium() || curve.level(xp) < t || t > curve.max()) {
                    return false;
                }
            }
            if (!PassStore.claim(c, who.profile(), who.season(), PassTable.Track.FREE, free, who.profile(), who.player(), now)
                    || !PassStore.claim(c, who.player(), who.season(), PassTable.Track.PREMIUM, premium, who.profile(), who.player(), now)) {
                return false;
            }
            PassStore.insertGrants(c, rows);
            return true;
        };
        return new Plan(txs, guard, rows, new Summary(money, crystals, keys, boosters, List.copyOf(cosmetics)), List.copyOf(tiers));
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
        Transaction t = Transaction.deposit(AccountRef.player(profile), currency, amount, TxReason.PASS_REWARD)
                .actor(actor)
                .reference(PassStore.cut(key, 64))
                .idempotencyKey(PassStore.cut(key + ":" + currency.key(), 96));
        for (Map.Entry<String, Object> d : details.entrySet()) {
            t = t.detail(d.getKey(), d.getValue());
        }
        txs.add(t);
    }

    private static PassStore.GrantRow grant(Claimer who, int tier, String kind, String ref, long amount, long now) {
        return new PassStore.GrantRow(UUID.randomUUID(), who.profile(), who.player(), who.season(), tier, kind, PassStore.cut(ref, 64), amount,
                PassStore.PENDING, now);
    }

    static String hex(UUID id) {
        return id.toString().replace("-", "");
    }
}

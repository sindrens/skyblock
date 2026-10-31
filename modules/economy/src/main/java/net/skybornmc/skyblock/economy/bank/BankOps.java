package net.skybornmc.skyblock.economy.bank;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.profile.ProfileService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The bank operations (M3.3) without Bukkit: deposit, withdraw, tier purchase and interest settlement. Every balance
 * change is one ledger transaction (deposit, interest, tier) or one atomic batch (withdrawal + its fee) through
 * {@link EconomyService}; the bank's interest state, capacity check and island withdraw counter are checked and written in
 * the <b>same database transaction</b> (the guard locks the bank's state row first, so all bank changes of one account are
 * serialized). All futures complete off the main thread.
 */
final class BankOps {

    /** The M3.3 period length (3 h): every bank state written before BANK2 counts periods of this length. */
    static final long LEGACY_PERIOD_MILLIS = 3 * 3_600_000L;

    /** The money rules of one bank account at the moment of an operation. */
    record Terms(long capacity, long payoutCap, double ratePercent, boolean earning) {
    }

    /** Daily island withdraw limit of one member ({@code limit} = Long.MAX_VALUE: none). */
    record Limit(UUID island, UUID member, long limit) {
    }

    enum Status {
        OK,
        /** The purse lacks the money (deposit, tier). */
        NO_MONEY,
        /** The bank lacks the money (withdraw). */
        BANK_EMPTY,
        /** The bank can't hold that much. */
        FULL,
        /** The daily island withdraw limit would be passed. */
        LIMIT,
        /** Below the smallest amount (or nothing left after the fee). */
        BELOW_MIN,
        /** P4: another profile of the same player. */
        OWN_PROFILE,
        /** Not this purse's bank. */
        NOT_YOURS,
        /** The highest tier is reached. */
        MAX_TIER,
        /** The bank changed at the same moment; try again. */
        BUSY,
        ERROR
    }

    /** Result of a deposit, withdrawal or tier purchase. {@code bank}/{@code purse}: balances after (null if unknown). */
    record Outcome(Status status, long amount, long fee, Long bank, Long purse) {

        static Outcome of(Status status) {
            return new Outcome(status, 0, 0, null, null);
        }

        boolean ok() {
            return status == Status.OK;
        }
    }

    private final EconomyService economy;
    private final BankStore store;
    private final Supplier<BankConfig> config;
    private final Supplier<BankTiers> tiers;
    private final Supplier<ProfileService> profiles;
    private final LongSupplier clock;
    private final IntSupplier today;
    private final Logger log;
    /** Period length in effect since the bank started (a config change needs a restart: states are converted then). */
    private final long periodMillis;

    BankOps(EconomyService economy, BankStore store, Supplier<BankConfig> config, Supplier<BankTiers> tiers,
            Supplier<ProfileService> profiles, LongSupplier clock, IntSupplier today, Logger log) {
        this.economy = economy;
        this.store = store;
        this.periodMillis = store.periodMillis();
        this.config = config;
        this.tiers = tiers;
        this.profiles = profiles;
        this.clock = clock;
        this.today = today;
        this.log = log;
    }

    BankStore store() {
        return store;
    }

    long periodMillis() {
        return periodMillis;
    }

    long currentPeriod() {
        return InterestMath.period(clock.getAsLong(), periodMillis());
    }

    int today() {
        return today.getAsInt();
    }

    // ------------------------------------------------------------------------------------------------ terms

    /** A profile's personal bank terms (tier from its state; tier 1 when never used). */
    CompletableFuture<Terms> personalTerms(UUID profileKey, boolean earning) {
        return store.state(AccountRef.bank(profileKey)).thenApply(s -> personalTerms(s.map(InterestMath.State::tier).orElse(1), earning));
    }

    Terms personalTerms(int tier, boolean earning) {
        BankTiers.PersonalTier t = tiers.get().tier(tier);
        return new Terms(t.capacity(), t.payoutCap(), config.get().personalRatePercent, earning);
    }

    Terms islandTerms(int capacityLevel, int interestLevel, boolean earning) {
        BankTiers t = tiers.get();
        return new Terms(t.islandCapacity(capacityLevel), t.islandCap(interestLevel), config.get().islandRatePercent, earning);
    }

    /** True if one of {@code profiles} played enough in the activity window (or the rule is off). */
    CompletableFuture<Boolean> active(java.util.Collection<UUID> profileKeys) {
        BankConfig cfg = config.get();
        if (cfg.activityMinutes <= 0) {
            return CompletableFuture.completedFuture(true);
        }
        return store.bestActivity(profileKeys, today() - cfg.activityDays + 1).thenApply(m -> m >= cfg.activityMinutes);
    }

    /** The most minutes one of {@code profiles} played in the activity window. */
    CompletableFuture<Integer> activityMinutes(java.util.Collection<UUID> profileKeys) {
        return store.bestActivity(profileKeys, today() - config.get().activityDays + 1);
    }

    // ------------------------------------------------------------------------------------------------ interest

    /** What one settlement did. */
    record Settled(long paid, int periods) {
        static final Settled NONE = new Settled(0, 0);
    }

    private record Snapshot(Optional<InterestMath.State> state, long balance) {
    }

    /**
     * Pays every finished period of the account that is still open (at most {@code interest.catch-up-periods}), one
     * ledger transaction per period with the key {@code bank_interest:<account>:<minutes>m:<period>}. Safe to run any number of
     * times, also at once from several threads: a period is paid at most once.
     */
    CompletableFuture<Settled> settle(AccountRef account, Terms terms) {
        return settleStep(account, terms, Settled.NONE, 0);
    }

    private CompletableFuture<Settled> settleStep(AccountRef account, Terms terms, Settled sofar, int step) {
        BankConfig cfg = config.get();
        if (step > cfg.catchUpPeriods + 4) {
            return CompletableFuture.completedFuture(sofar);
        }
        return store.db().async(c -> new Snapshot(BankStore.loadState(c, account), BankStore.balance(c, account))).thenCompose(snap -> {
            if (snap.state().isEmpty()) {
                return CompletableFuture.completedFuture(sofar);
            }
            InterestMath.State state = snap.state().get();
            long lastDue = currentPeriod() - 1;
            if (state.settledCycle() >= lastDue) {
                return CompletableFuture.completedFuture(sofar);
            }
            long skip = InterestMath.skipTo(state, lastDue, cfg.catchUpPeriods);
            if (skip > state.settledCycle()) {
                return store.skipTo(account, state.settledCycle(), skip).thenCompose(ok -> settleStep(account, terms, sofar, step + 1));
            }
            long next = state.settledCycle() + 1;
            long basis = InterestMath.basis(state, next, snap.balance());
            long amount = terms.earning()
                    ? InterestMath.payout(basis, terms.ratePercent(), terms.payoutCap(), BankRules.room(terms.capacity(), snap.balance()))
                    : 0;
            if (amount <= 0) {
                return store.markSettledAsync(account, next, state.cycle()).thenCompose(ok -> settleStep(account, terms, sofar, step + 1));
            }
            Transaction tx = Transaction.deposit(account, Currency.MONEY, amount, TxReason.INTEREST)
                    .actor(Actor.system("bank"))
                    .reference("period " + next)
                    .idempotencyKey(interestKey(account, periodMillis, next))
                    .detail("period", next).detail("period_hours", periodMillis / 3_600_000d).detail("basis", basis).detail("rate_percent", terms.ratePercent())
                    .detail("payout_cap", terms.payoutCap());
            return economy.executeGuarded(tx, c -> store.markSettled(c, account, next, state.cycle())).thenCompose(result -> {
                Settled now = result.ok() ? new Settled(sofar.paid() + amount, sofar.periods() + 1) : sofar;
                if (result.status() == TxResult.Status.ERROR || result.status() == TxResult.Status.INVALID) {
                    log.warning("Bank: interest for " + account + " period " + next + " failed (" + result.status() + "), retried later");
                    return CompletableFuture.completedFuture(now);
                }
                // OVERFLOW can't happen (capped by room); DUPLICATE = settled meanwhile by another run: read again.
                return settleStep(account, terms, now, step + 1);
            });
        });
    }

    /**
     * The payout key of one period. It names the period length (minutes) because period numbers of different lengths
     * mean different times (BANK2: 3 h → 8 h); M3.3 keys had no length and only ever used 3 h numbers, so 3 h keeps
     * that old form (a period paid before the update is still recognised).
     */
    static String interestKey(AccountRef account, long periodMillis, long period) {
        if (periodMillis == LEGACY_PERIOD_MILLIS) {
            return "bank_interest:" + account + ":" + period;
        }
        return "bank_interest:" + account + ":" + (periodMillis / 60_000L) + "m:" + period;
    }

    // ------------------------------------------------------------------------------------------------ deposit

    /** Why a guard said no (set inside the transaction; a guard may run more than once, so it starts with NONE). */
    private static final class Flag {
        volatile Status status;
    }

    /**
     * Moves {@code amount} from the purse ({@code purseKey}: profile key) into {@code bank}. Personal banks: only the same
     * profile's bank (P4). Island banks: the caller checked membership and the role permission on the main thread.
     */
    CompletableFuture<Outcome> deposit(UUID purseKey, AccountRef bank, long amount, Terms terms, Actor actor) {
        Status access = access(purseKey, bank, actor, "bank_deposit");
        if (access != Status.OK) {
            return CompletableFuture.completedFuture(Outcome.of(access));
        }
        if (amount < config.get().depositMin) {
            return CompletableFuture.completedFuture(Outcome.of(Status.BELOW_MIN));
        }
        return prepare(bank, terms).thenCompose(v -> depositOnce(purseKey, bank, amount, terms, actor))
                .thenCompose(out -> out.status() == Status.BUSY
                        ? prepare(bank, terms).thenCompose(v -> depositOnce(purseKey, bank, amount, terms, actor)) : CompletableFuture.completedFuture(out))
                .exceptionally(e -> fail("deposit", bank, e));
    }

    private CompletableFuture<Void> prepare(AccountRef bank, Terms terms) {
        return settle(bank, terms).thenCompose(s -> store.ensureState(bank, currentPeriod()));
    }

    private CompletableFuture<Outcome> depositOnce(UUID purseKey, AccountRef bank, long amount, Terms terms, Actor actor) {
        Flag flag = new Flag();
        long period = currentPeriod();
        Transaction tx = Transaction.transfer(AccountRef.player(purseKey), bank, Currency.MONEY, amount, TxReason.BANK_DEPOSIT)
                .actor(actor).reference(bank.isBank() ? "personal bank" : "island bank").detail("bank", bank.toString());
        return economy.executeGuarded(tx, c -> {
            flag.status = null;
            Optional<InterestMath.State> locked = BankStore.lockState(c, bank);
            if (locked.isEmpty() || !InterestMath.settledUpTo(locked.get(), period)) {
                flag.status = Status.BUSY;
                return false;
            }
            long before = BankStore.balance(c, bank);
            if (amount > BankRules.room(terms.capacity(), before)) {
                flag.status = Status.FULL;
                return false;
            }
            store.saveCycle(c, bank, InterestMath.afterChange(locked.get(), period, before, amount, false));
            return true;
        }).thenApply(r -> switch (r.status()) {
            case SUCCESS -> new Outcome(Status.OK, amount, 0, r.toBalance(), r.fromBalance());
            case INSUFFICIENT_FUNDS -> Outcome.of(Status.NO_MONEY);
            case OVERFLOW -> Outcome.of(Status.FULL);
            case DUPLICATE -> Outcome.of(flag.status == null ? Status.BUSY : flag.status);
            default -> Outcome.of(Status.ERROR);
        });
    }

    // ------------------------------------------------------------------------------------------------ withdraw

    /** True in the free minutes right after a payout (withdrawals cost no fee and keep the period's interest). */
    boolean freeNow() {
        return InterestMath.inFreeWindow(clock.getAsLong(), periodMillis, config.get().withdrawFreeMinutes);
    }

    /**
     * The fee a withdrawal of {@code amount} costs right now at {@code feePercent} (the island's mastery-lowered fee,
     * {@link WithdrawFee}): 0 in the free minutes after a payout.
     */
    long feeNow(long amount, double feePercent) {
        if (freeNow()) {
            return 0;
        }
        return InterestMath.fee(amount, feePercent);
    }

    /**
     * Takes {@code amount} out of {@code bank}: the purse gets {@code amount - fee}, the fee leaves the economy, both in one
     * atomic batch (owner R3-41: the fee is the only rule, the period's interest is kept). {@code limit}: island daily
     * limit or null.
     * {@code feePercent}: the fee after island masteries ({@link WithdrawFee}, decided on the main thread).
     */
    CompletableFuture<Outcome> withdraw(UUID purseKey, AccountRef bank, long amount, Terms terms, Limit limit, double feePercent,
                                        Actor actor) {
        Status access = access(purseKey, bank, actor, "bank_withdraw");
        if (access != Status.OK) {
            return CompletableFuture.completedFuture(Outcome.of(access));
        }
        if (amount < config.get().withdrawMin) {
            return CompletableFuture.completedFuture(Outcome.of(Status.BELOW_MIN));
        }
        return prepare(bank, terms).thenCompose(v -> withdrawOnce(purseKey, bank, amount, limit, feePercent, actor))
                .thenCompose(out -> out.status() == Status.BUSY
                        ? prepare(bank, terms).thenCompose(v -> withdrawOnce(purseKey, bank, amount, limit, feePercent, actor))
                        : CompletableFuture.completedFuture(out))
                .exceptionally(e -> fail("withdraw", bank, e));
    }

    private CompletableFuture<Outcome> withdrawOnce(UUID purseKey, AccountRef bank, long amount, Limit limit, double feePercent,
                                                    Actor actor) {
        long fee = feeNow(amount, feePercent);
        long net = amount - fee;
        if (net <= 0) {
            return CompletableFuture.completedFuture(Outcome.of(Status.BELOW_MIN));
        }
        Flag flag = new Flag();
        long period = currentPeriod();
        int day = today();
        String where = bank.isBank() ? "personal bank" : "island bank";
        List<Transaction> txs = new ArrayList<>(2);
        txs.add(Transaction.transfer(bank, AccountRef.player(purseKey), Currency.MONEY, net, TxReason.BANK_WITHDRAW)
                .actor(actor).reference(where).detail("bank", bank.toString()).detail("fee", fee).detail("fee_percent", feePercent).detail("requested", amount));
        if (fee > 0) {
            txs.add(Transaction.withdraw(bank, Currency.MONEY, fee, TxReason.BANK_FEE).actor(actor).reference(where)
                    .detail("bank", bank.toString()).detail("withdrawn", amount));
        }
        return economy.executeBatch(txs, c -> {
            flag.status = null;
            Optional<InterestMath.State> locked = BankStore.lockState(c, bank);
            if (locked.isEmpty() || !InterestMath.settledUpTo(locked.get(), period)) {
                flag.status = Status.BUSY;
                return false;
            }
            long before = BankStore.balance(c, bank);
            if (before < amount) {
                flag.status = Status.BANK_EMPTY;
                return false;
            }
            if (limit != null && !BankStore.addWithdrawn(c, limit.island(), limit.member(), day, amount, limit.limit())) {
                flag.status = Status.LIMIT;
                return false;
            }
            store.saveCycle(c, bank, InterestMath.afterChange(locked.get(), period, before, -amount, false));
            return true;
        }).thenApply((BatchResult r) -> {
            if (r.ok()) {
                TxResult last = r.results().getLast();
                Long bankAfter = fee > 0 ? last.fromBalance() : r.results().getFirst().fromBalance();
                return new Outcome(Status.OK, amount, fee, bankAfter, r.results().getFirst().toBalance());
            }
            return switch (r.status()) {
                case INSUFFICIENT_FUNDS -> Outcome.of(Status.BANK_EMPTY);
                case DUPLICATE -> Outcome.of(flag.status == null ? Status.BUSY : flag.status);
                default -> Outcome.of(Status.ERROR);
            };
        });
    }

    // ------------------------------------------------------------------------------------------------ tiers

    /** Buys the next personal tier for the profile {@code purseKey} with money from its purse. */
    CompletableFuture<Outcome> buyTier(UUID purseKey, int expectedTier, Actor actor) {
        AccountRef bank = AccountRef.bank(purseKey);
        BankTiers t = tiers.get();
        if (expectedTier >= t.maxTier()) {
            return CompletableFuture.completedFuture(Outcome.of(Status.MAX_TIER));
        }
        int next = expectedTier + 1;
        long cost = t.tier(next).cost();
        if (cost <= 0) {
            return CompletableFuture.completedFuture(Outcome.of(Status.ERROR));
        }
        return store.ensureState(bank, currentPeriod()).thenCompose(v -> economy.executeGuarded(
                Transaction.withdraw(AccountRef.player(purseKey), Currency.MONEY, cost, TxReason.BANK_TIER).actor(actor)
                        .reference("tier " + next).detail("tier", next)
                        .detail("capacity", t.tier(next).capacity()),
                // The tier row is compared-and-set in the same transaction: a double click can never buy a tier twice.
                c -> store.raiseTier(c, bank, expectedTier))).thenApply(r -> switch (r.status()) {
                    case SUCCESS -> new Outcome(Status.OK, cost, 0, null, r.fromBalance());
                    case INSUFFICIENT_FUNDS -> Outcome.of(Status.NO_MONEY);
                    case DUPLICATE -> Outcome.of(Status.BUSY);
                    default -> Outcome.of(Status.ERROR);
                }).exceptionally(e -> fail("tier", bank, e));
    }

    // ------------------------------------------------------------------------------------------------ closing

    /** Ledger reason (and audit action) of island bank money deleted with its island (owner answer 263). */
    static final String REASON_DELETED = "bank_island_deleted";

    /**
     * Deletes everything left in a removed island's bank (owner answer 263: the money belongs to the island). One ledger
     * withdrawal, so the amount stays in /transactions and /logs; once per island ({@code bank_deleted:<island>}).
     * Completes with the amount deleted (0 if empty or already deleted).
     */
    CompletableFuture<Long> deleteMoney(AccountRef bank, Actor actor) {
        return store.db().async(c -> BankStore.balance(c, bank)).thenCompose(balance -> {
            if (balance <= 0) {
                return CompletableFuture.completedFuture(0L);
            }
            return economy.execute(Transaction.withdraw(bank, Currency.MONEY, balance, REASON_DELETED)
                    .actor(actor).reference("island removed").idempotencyKey("bank_deleted:" + bank.owner()))
                    .thenApply(r -> r.ok() ? balance : 0L);
        });
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** P4 for personal banks (island banks are checked by the caller on the main thread). */
    private Status access(UUID purseKey, AccountRef bank, Actor actor, String path) {
        if (!bank.isBank()) {
            return bank.isIsland() ? Status.OK : Status.NOT_YOURS;
        }
        return switch (BankRules.personal(purseKey, bank.owner(), profiles.get())) {
            case OK -> Status.OK;
            case OWN_PROFILE -> {
                profiles.get().blockOwnTransfer(AuditCategory.ECONOMY, path, purseKey, bank.owner(), actor);
                yield Status.OWN_PROFILE;
            }
            default -> Status.NOT_YOURS;
        };
    }

    private Outcome fail(String what, AccountRef bank, Throwable e) {
        log.log(Level.WARNING, "Bank: " + what + " on " + bank + " failed", e);
        return Outcome.of(Status.ERROR);
    }
}

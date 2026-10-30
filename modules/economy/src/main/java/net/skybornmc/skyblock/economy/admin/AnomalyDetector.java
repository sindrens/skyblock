package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.TxReason;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The anomaly rules of the economy watch (M3.8). Fed by the background job, one batch of ledger rows at a time in id order
 * (never on the main thread, one caller at a time). Finds:
 * <ul>
 *   <li>{@link Rule#EARN_RATE}: a profile earned more in one hour than {@code multiplier} x the baseline (the average
 *       earning per active profile-hour over the last days) and at least the minimum;</li>
 *   <li>{@link Rule#BIG_TRANSFER}: one transfer (or one creation) at or above a share of the money supply;</li>
 *   <li>{@link Rule#REPEAT_TRANSFER}: the same payer sent the same receiver near-identical amounts many times in a window;</li>
 *   <li>{@link Rule#NOTE_REDEEM}: many money notes cashed by one profile in one hour;</li>
 *   <li>{@link Rule#SELL_SPIKE}: server-wide selling in one hour above {@code multiplier} x the hourly average;</li>
 *   <li>{@link Rule#BALANCE_MISMATCH}: an account's balance before a transaction is not what its previous transaction
 *       left: the balance changed without a ledger row.</li>
 * </ul>
 * Never punishes; the drafts become staff alerts. Repeated alerts are held back by a cooldown per rule and subject.
 * State lives in memory only (a restart forgets windows and last balances; nothing wrong is reported because of that).
 */
public final class AnomalyDetector {

    /** What the rules compare with, read from the summary tables before each run. */
    public record Baselines(Map<Currency, Long> earnPerHour, long sellPerHour, Map<Currency, Long> supply) {
        public static final Baselines NONE = new Baselines(Map.of(), 0, Map.of());

        public Baselines {
            earnPerHour = earnPerHour == null ? Map.of() : Map.copyOf(earnPerHour);
            supply = supply == null ? Map.of() : Map.copyOf(supply);
        }

        long earn(Currency c) {
            return earnPerHour.getOrDefault(c, 0L);
        }

        long supplyOf(Currency c) {
            return supply.getOrDefault(c, 0L);
        }
    }

    private record Hit(long id, long time, long amount) {
    }

    private record PairKey(UUID from, UUID to, Currency currency) {
    }

    private record BalanceKey(AccountRef account, Currency currency) {
    }

    private static final class NoteCount {
        int count;
        final Deque<Long> ids = new ArrayDeque<>();
    }

    private final Supplier<WatchConfig> config;
    private final Map<String, Long> lastSent = new HashMap<>();
    private final Map<PairKey, Deque<Hit>> pairs = new HashMap<>();
    private final Map<Aggregation.AccountKey, NoteCount> notes = new HashMap<>();
    private final LinkedHashMap<BalanceKey, Long> balances = new LinkedHashMap<>(1024, 0.75f, true);
    private long lastId;

    public AnomalyDetector(Supplier<WatchConfig> config) {
        this.config = config;
    }

    /** Forgets every window, count and balance (after a failed batch, so nothing is counted twice). Cooldowns stay. */
    public void reset() {
        pairs.clear();
        notes.clear();
        balances.clear();
        lastId = 0;
    }

    // ================================================================== per row

    /**
     * Checks each row of a batch (ids ascending). Rows at or below the last id seen are ignored.
     *
     * @return drafts found in the rows (before the cooldown; see {@link #filter})
     */
    public List<AlertDraft> rows(List<LedgerRow> rows, Baselines base, long now) {
        WatchConfig c = config.get();
        Set<Currency> currencies = c.alertCurrencySet();
        List<AlertDraft> out = new ArrayList<>();
        for (LedgerRow row : rows) {
            if (row.id() <= lastId) {
                continue;
            }
            lastId = row.id();
            if (c.mismatchEnabled) {
                mismatch(row, c, out);
            }
            if (row.amount() <= 0) {
                continue;
            }
            if (c.bigTransferEnabled) {
                big(row, c, base, out);
            }
            if (c.repeatEnabled && currencies.contains(row.currency())) {
                repeat(row, c, out);
            }
            if (c.noteEnabled && currencies.contains(row.currency())) {
                note(row, c, out);
            }
        }
        pruneNotes(Buckets.hour(now));
        if (pairs.size() > 50_000) {
            prunePairs(now, c);
        }
        return out;
    }

    /** The big-transfer threshold: the larger of the minimum and the supply share. */
    public static long bigThreshold(WatchConfig c, Currency currency, long supply) {
        double share = Math.max(0, supply) * (c.bigTransferSupplyPercent / 100.0);
        long fromSupply = share >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) share;
        return Math.max(c.bigMin(currency), fromSupply);
    }

    private void big(LedgerRow row, WatchConfig c, Baselines base, List<AlertDraft> out) {
        if (staffReason(row.reason()) || c.ignoreReasons.contains(row.reason())) {
            return;
        }
        Flow flow = row.flow();
        if (flow == Flow.REMOVED || (flow == Flow.CREATED && !c.bigIncludeCreated)) {
            return;
        }
        if (flow == Flow.MOVED && (!row.from().isPlayer() || !row.to().isPlayer())) {
            return; // bank and island bank moves are the player's own money
        }
        long threshold = bigThreshold(c, row.currency(), base.supplyOf(row.currency()));
        if (row.amount() < threshold) {
            return;
        }
        UUID subject = flow == Flow.MOVED ? row.from().owner() : row.to().isPlayer() ? row.to().owner() : null;
        UUID other = flow == Flow.MOVED ? row.to().owner() : null;
        AlertDraft.Severity severity = row.amount() / 4 >= threshold ? AlertDraft.Severity.HIGH : AlertDraft.Severity.WATCH;
        out.add(new AlertDraft(Rule.BIG_TRANSFER, severity, row.currency(), subject, other, row.amount(), threshold, 1,
                row.hour(), List.of(row.id())));
    }

    private static boolean staffReason(String reason) {
        return reason.startsWith("admin_");
    }

    /** Whether two amounts are within {@code percent} of the larger one. */
    public static boolean similar(long a, long b, double percent) {
        long max = Math.max(a, b);
        long diff = Math.abs(a - b);
        return diff <= max * (percent / 100.0);
    }

    private void repeat(LedgerRow row, WatchConfig c, List<AlertDraft> out) {
        if (row.flow() != Flow.MOVED || !row.from().isPlayer() || !row.to().isPlayer() || row.amount() < c.repeatMinAmount
                || c.ignoreReasons.contains(row.reason())) {
            return;
        }
        long window = c.repeatWindowMinutes * 60_000L;
        PairKey key = new PairKey(row.from().owner(), row.to().owner(), row.currency());
        Deque<Hit> hits = pairs.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!hits.isEmpty() && row.time() - hits.peekFirst().time() > window) {
            hits.pollFirst();
        }
        hits.addLast(new Hit(row.id(), row.time(), row.amount()));
        List<Long> similar = new ArrayList<>();
        for (Hit h : hits) {
            if (similar(h.amount(), row.amount(), c.repeatTolerancePercent)) {
                similar.add(h.id());
            }
        }
        if (similar.size() >= c.repeatCount) {
            long total = 0;
            for (Hit h : hits) {
                if (similar.contains(h.id())) {
                    total = Aggregation.add(total, h.amount());
                }
            }
            out.add(new AlertDraft(Rule.REPEAT_TRANSFER, AlertDraft.Severity.WATCH, row.currency(), key.from(), key.to(), total,
                    c.repeatCount, similar.size(), row.hour(), last(similar, c.evidenceRows)));
        }
        while (hits.size() > Math.max(c.repeatCount * 4, 64)) {
            hits.pollFirst();
        }
    }

    private void note(LedgerRow row, WatchConfig c, List<AlertDraft> out) {
        if (!TxReason.NOTE_REDEEM.equals(row.reason()) || row.to() == null || !row.to().isPlayer()) {
            return;
        }
        Aggregation.AccountKey key = new Aggregation.AccountKey(row.hour(), row.to().owner(), row.currency());
        NoteCount count = notes.computeIfAbsent(key, k -> new NoteCount());
        count.count++;
        count.ids.addLast(row.id());
        while (count.ids.size() > c.evidenceRows) {
            count.ids.pollFirst();
        }
        if (count.count >= c.noteCount) {
            out.add(new AlertDraft(Rule.NOTE_REDEEM, AlertDraft.Severity.WATCH, row.currency(), key.owner(), null, row.amount(),
                    c.noteCount, count.count, row.hour(), List.copyOf(count.ids)));
        }
    }

    private void mismatch(LedgerRow row, WatchConfig c, List<AlertDraft> out) {
        if (row.from() != null && row.fromBalance() != null) {
            check(row, row.from(), row.fromBalance(), Aggregation.add(row.fromBalance(), row.amount()), c, out);
        }
        if (row.to() != null && row.toBalance() != null) {
            check(row, row.to(), row.toBalance(), row.toBalance() - row.amount(), c, out);
        }
        while (balances.size() > c.mismatchTracked) {
            Iterator<BalanceKey> it = balances.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    private void check(LedgerRow row, AccountRef account, long after, long before, WatchConfig c, List<AlertDraft> out) {
        BalanceKey key = new BalanceKey(account, row.currency());
        Long known = balances.put(key, after);
        if (known == null || known == before) {
            return;
        }
        long jump = Math.abs(before - known);
        UUID subject = account.isPlayer() ? account.owner() : null;
        out.add(new AlertDraft(Rule.BALANCE_MISMATCH, AlertDraft.Severity.HIGH, row.currency(), subject,
                account.isPlayer() ? null : account.owner(), jump, known, 1, row.hour(), List.of(row.id())));
    }

    // ================================================================== totals after a batch

    /** The earn-rate threshold of a currency: the larger of the minimum and {@code multiplier} x the baseline. */
    public static long earnThreshold(WatchConfig c, Currency currency, long baselinePerHour) {
        double scaled = Math.max(0, baselinePerHour) * c.earnMultiplier;
        long fromBase = scaled >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.ceil(scaled);
        return Math.max(c.earnMin(currency), fromBase);
    }

    /**
     * Checks the new hourly totals of the profiles touched by a batch (earn-rate).
     *
     * @param totals   new totals after the batch was added (whole hour, not just the batch)
     * @param evidence the batch's rows (for evidence ids)
     */
    public List<AlertDraft> accountTotals(Map<Aggregation.AccountKey, Aggregation.AccountSum> totals, List<LedgerRow> evidence,
                                          Baselines base) {
        WatchConfig c = config.get();
        if (!c.earnRateEnabled) {
            return List.of();
        }
        Set<Currency> currencies = c.alertCurrencySet();
        List<AlertDraft> out = new ArrayList<>();
        for (Map.Entry<Aggregation.AccountKey, Aggregation.AccountSum> e : totals.entrySet()) {
            Aggregation.AccountKey key = e.getKey();
            if (!currencies.contains(key.currency())) {
                continue;
            }
            long threshold = earnThreshold(c, key.currency(), base.earn(key.currency()));
            long earned = e.getValue().earned();
            if (earned <= threshold) {
                continue;
            }
            AlertDraft.Severity severity = earned / 2 > threshold ? AlertDraft.Severity.HIGH : AlertDraft.Severity.WATCH;
            out.add(new AlertDraft(Rule.EARN_RATE, severity, key.currency(), key.owner(), null, earned, threshold,
                    (int) Math.min(Integer.MAX_VALUE, e.getValue().count()), key.hour(),
                    biggestIncoming(evidence, key, c.evidenceRows, Set.copyOf(c.ignoreReasons))));
        }
        return out;
    }

    private static List<Long> biggestIncoming(List<LedgerRow> rows, Aggregation.AccountKey key, int max, Set<String> ignore) {
        return rows.stream()
                .filter(r -> r.hour() == key.hour() && r.currency() == key.currency() && r.to() != null && r.to().isPlayer()
                        && r.to().owner().equals(key.owner()) && !ignore.contains(r.reason()))
                .sorted(Comparator.comparingLong(LedgerRow::amount).reversed())
                .limit(max)
                .map(LedgerRow::id)
                .sorted()
                .toList();
    }

    /** The sell-spike threshold: the larger of the minimum and {@code multiplier} x the hourly average. */
    public static long sellThreshold(WatchConfig c, long sellPerHour) {
        double scaled = Math.max(0, sellPerHour) * c.sellSpikeMultiplier;
        long fromBase = scaled >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.ceil(scaled);
        return Math.max(c.sellSpikeMin, fromBase);
    }

    /** Checks the new hourly flow totals touched by a batch (sell-spike, money only). */
    public List<AlertDraft> flowTotals(Map<Aggregation.FlowKey, Aggregation.Sum> totals, Baselines base) {
        WatchConfig c = config.get();
        if (!c.sellSpikeEnabled) {
            return List.of();
        }
        List<AlertDraft> out = new ArrayList<>();
        long threshold = sellThreshold(c, base.sellPerHour());
        for (Map.Entry<Aggregation.FlowKey, Aggregation.Sum> e : totals.entrySet()) {
            Aggregation.FlowKey key = e.getKey();
            if (key.currency() != Currency.MONEY || key.flow() != Flow.CREATED || !key.reason().equals(c.sellReason)) {
                continue;
            }
            long sold = e.getValue().amount();
            if (sold > threshold) {
                out.add(new AlertDraft(Rule.SELL_SPIKE, sold / 2 > threshold ? AlertDraft.Severity.HIGH : AlertDraft.Severity.WATCH,
                        Currency.MONEY, null, null, sold, threshold, (int) Math.min(Integer.MAX_VALUE, e.getValue().count()),
                        key.hour(), List.of()));
            }
        }
        return out;
    }

    // ================================================================== cooldown

    /**
     * Drops drafts sent within the cooldown (same rule and subject) and keeps one per cooldown key per call (the
     * strongest). Records the kept ones as sent at {@code now}.
     */
    public List<AlertDraft> filter(List<AlertDraft> drafts, long now) {
        long cooldown = config.get().cooldownMinutes * 60_000L;
        lastSent.values().removeIf(t -> now - t >= cooldown);
        Map<String, AlertDraft> best = new LinkedHashMap<>();
        for (AlertDraft d : drafts) {
            String key = d.cooldownKey();
            if (lastSent.containsKey(key)) {
                continue;
            }
            best.merge(key, d, (a, b) -> b.amount() > a.amount() ? b : a);
        }
        for (String key : best.keySet()) {
            lastSent.put(key, now);
        }
        return List.copyOf(best.values());
    }

    // ================================================================== housekeeping

    private void pruneNotes(long currentHour) {
        notes.keySet().removeIf(k -> k.hour() < currentHour - 1);
    }

    private void prunePairs(long now, WatchConfig c) {
        long window = c.repeatWindowMinutes * 60_000L;
        pairs.values().removeIf(d -> d.isEmpty() || now - d.peekLast().time() > window);
    }

    private static List<Long> last(List<Long> ids, int max) {
        return ids.size() <= max ? List.copyOf(ids) : List.copyOf(ids.subList(ids.size() - max, ids.size()));
    }

    /** For tests: the number of tracked balances. */
    int trackedBalances() {
        return balances.size();
    }
}

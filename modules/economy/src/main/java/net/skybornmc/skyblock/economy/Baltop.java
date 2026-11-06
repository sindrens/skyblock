package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

/**
 * {@code /baltop} lists. Each currency's list is one cheap indexed query, always run on the database thread:
 * <ul>
 *   <li>a background task refreshes every list older than {@code baltop.refresh-seconds} (default 30 s);</li>
 *   <li>{@code /baltop} shows the cached list at once and, when it is older than that, starts a refresh in the
 *       background (the next {@code /baltop} shows it);</li>
 *   <li>at most one query per currency is in flight, so 300 players spamming the command still cause one query.</li>
 * </ul>
 * The viewer's own line is made live from their cached balance ({@link Snapshot#withLive}).
 */
final class Baltop {

    /** One currency's list and when it was read. */
    record Snapshot(List<EconomyStore.TopEntry> entries, long time) {

        /** 1-based position of {@code player}, or 0 if not listed. */
        int rank(UUID player) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).player().equals(player)) {
                    return i + 1;
                }
            }
            return 0;
        }

        /**
         * This list with {@code player}'s line replaced by their live balance (added when it now makes the top
         * {@code size}, removed when it dropped to 0), re-sorted richest first. The rest stays as cached.
         */
        Snapshot withLive(UUID player, String name, long liveBalance, int size) {
            List<EconomyStore.TopEntry> out = new ArrayList<>(entries.size() + 1);
            String knownName = name;
            for (EconomyStore.TopEntry e : entries) {
                if (e.player().equals(player)) {
                    if (knownName == null) {
                        knownName = e.name();
                    }
                } else {
                    out.add(e);
                }
            }
            if (liveBalance > 0) {
                out.add(new EconomyStore.TopEntry(player, knownName, liveBalance));
            }
            out.sort(Comparator.comparingLong(EconomyStore.TopEntry::balance).reversed());
            if (out.size() > size) {
                out = out.subList(0, size);
            }
            return new Snapshot(List.copyOf(out), time);
        }
    }

    private final Map<Currency, Snapshot> lists = new EnumMap<>(Currency.class);
    private final Set<Currency> inFlight = ConcurrentHashMap.newKeySet();
    private final BiFunction<Currency, Integer, CompletableFuture<List<EconomyStore.TopEntry>>> source;
    private final LongSupplier clock;

    Baltop(BiFunction<Currency, Integer, CompletableFuture<List<EconomyStore.TopEntry>>> source, LongSupplier clock) {
        this.source = source;
        this.clock = clock;
    }

    /** The current list, or {@code null} before the first calculation finished. */
    synchronized Snapshot get(Currency currency) {
        return lists.get(currency);
    }

    /** True when the list is missing or older than {@code maxAgeMillis}. */
    boolean stale(Currency currency, long maxAgeMillis) {
        Snapshot current = get(currency);
        return current == null || clock.getAsLong() - current.time() >= maxAgeMillis;
    }

    /**
     * Starts a background refresh of {@code currency} when its list is older than {@code maxAgeMillis} and no refresh
     * is running. Returns a future of the new list, or {@code null} when nothing was started.
     */
    CompletableFuture<Snapshot> refreshIfOlder(Currency currency, long maxAgeMillis, int size) {
        if (!stale(currency, maxAgeMillis) || !inFlight.add(currency)) {
            return null;
        }
        CompletableFuture<List<EconomyStore.TopEntry>> query;
        try {
            query = source.apply(currency, size);
        } catch (RuntimeException e) {
            inFlight.remove(currency);
            throw e;
        }
        return query.handle((entries, error) -> {
            try {
                if (error != null) {
                    return null; // keep the old list; the next tick or /baltop tries again
                }
                Snapshot fresh = new Snapshot(List.copyOf(entries), clock.getAsLong());
                synchronized (this) {
                    lists.put(currency, fresh);
                }
                return fresh;
            } finally {
                inFlight.remove(currency);
            }
        });
    }

    /** {@link #refreshIfOlder(Currency, long, int)} for every currency (tests). */
    void refreshIfOlder(long maxAgeMillis, int size) {
        refreshIfOlder(maxAgeMillis, size, List.of(Currency.values()));
    }

    /** {@link #refreshIfOlder(Currency, long, int)} for each of {@code currencies} (the background task: the public lists). */
    void refreshIfOlder(long maxAgeMillis, int size, java.util.Collection<Currency> currencies) {
        for (Currency currency : currencies) {
            refreshIfOlder(currency, maxAgeMillis, size);
        }
    }

    /** A refresh is running for {@code currency}. */
    boolean refreshing(Currency currency) {
        return inFlight.contains(currency);
    }
}

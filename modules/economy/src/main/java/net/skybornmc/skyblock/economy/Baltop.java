package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

/**
 * {@code /baltop} lists, recalculated at most every {@code baltop.refresh-minutes} (one cheap indexed query per currency),
 * so 300 players spamming the command never touch the database.
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
    }

    private final Map<Currency, Snapshot> lists = new EnumMap<>(Currency.class);
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

    /** Recalculates every currency whose list is older than {@code maxAgeMillis} (or missing). */
    void refreshIfOlder(long maxAgeMillis, int size) {
        long now = clock.getAsLong();
        for (Currency currency : Currency.values()) {
            Snapshot current = get(currency);
            if (current == null || now - current.time() >= maxAgeMillis) {
                source.apply(currency, size).thenAccept(entries -> {
                    synchronized (this) {
                        lists.put(currency, new Snapshot(List.copyOf(entries), clock.getAsLong()));
                    }
                });
            }
        }
    }
}

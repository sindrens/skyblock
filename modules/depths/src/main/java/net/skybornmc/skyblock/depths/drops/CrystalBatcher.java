package net.skybornmc.skyblock.depths.drops;

import net.skybornmc.skyblock.common.economy.TxResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Crystals from Depths drops (M4.9), paid in batches: every block adds to the profile's open amount (O(1), main thread),
 * and every few seconds one deposit per profile is made (ledger reason {@code depths_drop}). One ledger and audit row
 * per player per batch instead of one per block.
 *
 * <p>Retry safety: a batch keeps its idempotency key until it is settled. A database error is retried with the
 * <b>same</b> key, so a payout that did commit but whose answer was lost is refused as DUPLICATE instead of being paid
 * twice. Crystals found while a batch is in flight wait for the next one. Main thread only (results are handled on the
 * given executor, which must be the main thread at runtime).</p>
 */
public final class CrystalBatcher {

    /** Pays one batch: deposit {@code amount} crystals to {@code profile} with idempotency key {@code key}. */
    @FunctionalInterface
    public interface Payer {
        CompletableFuture<TxResult> pay(UUID profile, long amount, String key);
    }

    /** What happened to a batch. */
    public enum Outcome {
        /** Paid now. */
        PAID,
        /** Already paid by an earlier attempt with the same key (nothing paid now). */
        ALREADY_PAID,
        /** Failed for good (invalid, overflow, too many errors): dropped, logged by the caller. */
        DROPPED
    }

    /** Called on the executor when a batch is settled. */
    @FunctionalInterface
    public interface Listener {
        void settled(UUID profile, long amount, Outcome outcome, TxResult.Status status);
    }

    static final class Batch {
        final String key;
        final long amount;
        int attempts;
        boolean inFlight;

        Batch(String key, long amount) {
            this.key = key;
            this.amount = amount;
        }
    }

    private final Map<UUID, Long> open = new HashMap<>();
    private final Map<UUID, Batch> batches = new HashMap<>();
    private final Executor main;
    private final Supplier<String> keys;
    private final int maxAttempts;

    /**
     * @param main        where results are handled (the main thread)
     * @param keys        new idempotency keys ({@code depths_drop:<uuid>})
     * @param maxAttempts errors before a batch is dropped
     */
    public CrystalBatcher(Executor main, Supplier<String> keys, int maxAttempts) {
        this.main = main;
        this.keys = keys;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /** Adds crystals found by a profile (main thread). */
    public void add(UUID profile, long crystals) {
        if (crystals > 0) {
            open.merge(profile, crystals, Long::sum);
        }
    }

    /** Crystals found but not paid yet (open + in batches) for a profile. */
    public long pending(UUID profile) {
        Batch b = batches.get(profile);
        return open.getOrDefault(profile, 0L) + (b == null ? 0 : b.amount);
    }

    /** True if nothing is open or in a batch. */
    public boolean idle() {
        return open.isEmpty() && batches.isEmpty();
    }

    /** Starts the payout of every open amount and retries failed batches. Returns the payment futures (shutdown waits for them). */
    public List<CompletableFuture<?>> flush(Payer payer, Listener listener) {
        List<Map.Entry<UUID, Batch>> toSend = new ArrayList<>();
        // Retry batches that failed (same key).
        for (Map.Entry<UUID, Batch> e : batches.entrySet()) {
            if (!e.getValue().inFlight) {
                toSend.add(Map.entry(e.getKey(), e.getValue()));
            }
        }
        Iterator<Map.Entry<UUID, Long>> it = open.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            if (batches.containsKey(e.getKey())) {
                continue; // one batch per profile at a time; this waits for the next flush
            }
            Batch batch = new Batch(keys.get(), e.getValue());
            it.remove();
            batches.put(e.getKey(), batch);
            toSend.add(Map.entry(e.getKey(), batch));
        }
        List<CompletableFuture<?>> started = new ArrayList<>(toSend.size());
        for (Map.Entry<UUID, Batch> e : toSend) {
            started.add(send(e.getKey(), e.getValue(), payer, listener));
        }
        return started;
    }

    private CompletableFuture<?> send(UUID profile, Batch batch, Payer payer, Listener listener) {
        batch.inFlight = true;
        batch.attempts++;
        CompletableFuture<TxResult> future;
        try {
            future = payer.pay(profile, batch.amount, batch.key);
        } catch (RuntimeException e) {
            future = CompletableFuture.completedFuture(TxResult.of(TxResult.Status.ERROR));
        }
        CompletableFuture<TxResult> handled = future.handle((result, error) -> error != null || result == null
                ? TxResult.of(TxResult.Status.ERROR) : result);
        handled.thenAcceptAsync(result -> settle(profile, batch, result, listener), main);
        return handled;
    }

    private void settle(UUID profile, Batch batch, TxResult result, Listener listener) {
        batch.inFlight = false;
        if (batches.get(profile) != batch) {
            return;
        }
        switch (result.status()) {
            case SUCCESS -> {
                batches.remove(profile);
                listener.settled(profile, batch.amount, Outcome.PAID, result.status());
            }
            case DUPLICATE -> {
                batches.remove(profile);
                listener.settled(profile, batch.amount, Outcome.ALREADY_PAID, result.status());
            }
            case ERROR -> {
                if (batch.attempts >= maxAttempts) {
                    batches.remove(profile);
                    listener.settled(profile, batch.amount, Outcome.DROPPED, result.status());
                }
                // else: kept, retried with the same key on the next flush
            }
            default -> {
                batches.remove(profile);
                listener.settled(profile, batch.amount, Outcome.DROPPED, result.status());
            }
        }
    }
}

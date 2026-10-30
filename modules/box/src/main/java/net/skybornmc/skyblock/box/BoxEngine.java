package net.skybornmc.skyblock.box;

import net.skybornmc.skyblock.common.db.Database;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The box's database side, Bukkit-free (unit-tested on H2): deliveries in one transaction with retries and the spool as
 * the last resort, remove-then-give takes, give-backs, admin archive and spool import.
 */
final class BoxEngine {

    /** A delivery: new ids, the profile's count afterwards (-1 when spooled), and whether it went to the spool. */
    record Stored(List<Long> ids, int count, boolean spooled) {
    }

    /** A take: exactly the rows removed (give these and only these) and the count left. */
    record Taken(List<BoxEntry> entries, int count) {
    }

    private final Database db;
    private final BoxStore store;
    private final BoxSpool spool;
    private final Logger log;
    private final IntSupplier retries;
    private final IntSupplier retryDelayMs;

    BoxEngine(Database db, BoxStore store, BoxSpool spool, Logger log, IntSupplier retries, IntSupplier retryDelayMs) {
        this.db = db;
        this.store = store;
        this.spool = spool;
        this.log = log;
        this.retries = retries;
        this.retryDelayMs = retryDelayMs;
    }

    BoxStore store() {
        return store;
    }

    /** Stores all items in one transaction (retried); if the database keeps failing they go to the spool. Never fails. */
    CompletableFuture<Stored> deliver(UUID profile, Collection<BoxItem> items) {
        List<BoxItem> list = List.copyOf(items);
        if (list.isEmpty()) {
            return CompletableFuture.completedFuture(new Stored(List.of(), -1, false));
        }
        return attempt(() -> db.transaction(c -> {
            List<Long> ids = store.insert(c, profile, list);
            return new Stored(ids, store.count(c, profile), false);
        }), Math.max(0, retries.getAsInt())).exceptionally(error -> {
            try {
                spool.append(profile, list);
                log.log(Level.WARNING, "Box: the database refused a delivery of " + list.size() + " stack(s) for " + profile
                        + "; spooled to disk, imported when the database works again", Database.cause(error));
            } catch (Exception disk) {
                for (BoxItem i : list) {
                    log.log(Level.SEVERE, "Box: could not store an item for profile " + profile + " (database and spool failed). "
                            + "Restore it from this data: source=" + i.source() + " " + Base64.getEncoder().encodeToString(i.bytes()), disk);
                }
            }
            return new Stored(List.of(), -1, true);
        });
    }

    /** Removes the rows (one transaction); the caller gives exactly {@link Taken#entries()}. */
    CompletableFuture<Taken> take(UUID profile, List<Long> ids) {
        return safe(() -> db.transaction(c -> {
            List<BoxEntry> taken = store.take(c, profile, ids);
            return new Taken(taken, store.count(c, profile));
        }));
    }

    /** Puts rows that could not be given back into the box (original source and arrival time kept). */
    CompletableFuture<Stored> giveBack(UUID profile, List<BoxItem> items) {
        return deliver(profile, items);
    }

    CompletableFuture<List<BoxEntry>> list(UUID profile, int limit) {
        return safe(() -> db.async(c -> store.list(c, profile, limit)));
    }

    CompletableFuture<Map<UUID, Integer>> counts(Collection<UUID> profiles) {
        return safe(() -> db.async(c -> store.counts(c, profiles)));
    }

    /** Admin clear ({@code ids == null}) or remove: rows move to box_cleared. Completes with the moved rows. */
    CompletableFuture<List<BoxEntry>> archive(UUID profile, List<Long> ids, UUID by, String byName, long now) {
        return safe(() -> db.transaction(c -> store.archive(c, profile, ids, by, byName, now)));
    }

    /** Imports waiting spool files. Completes with the number of stacks stored (refs make a repeat harmless). */
    CompletableFuture<Integer> importSpool() {
        if (!spool.hasWork()) {
            return CompletableFuture.completedFuture(0);
        }
        List<BoxSpool.Batch> batches;
        try {
            batches = spool.takeForImport();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
        for (BoxSpool.Batch b : batches) {
            chain = chain.thenCompose(sum -> safe(() -> db.transaction(c -> {
                int n = 0;
                for (Map.Entry<UUID, List<BoxItem>> e : b.items().entrySet()) {
                    n += store.insert(c, e.getKey(), e.getValue()).size();
                }
                return n;
            })).thenApply(n -> {
                if (b.badLines() > 0) {
                    log.severe("Box: " + b.badLines() + " unreadable line(s) in " + b.file().getName() + "; the file is kept as "
                            + b.file().getName() + ".bad for staff");
                    try {
                        java.nio.file.Files.move(b.file().toPath(), new java.io.File(b.file().getParentFile(), b.file().getName() + ".bad").toPath());
                    } catch (Exception e) {
                        log.log(Level.WARNING, "Box: could not keep " + b.file().getName(), e);
                    }
                } else {
                    try {
                        spool.done(b);
                    } catch (Exception e) {
                        log.log(Level.WARNING, "Box: could not delete imported " + b.file().getName() + " (importing again is harmless)", e);
                    }
                }
                return sum + n;
            }));
        }
        return chain;
    }

    private <T> CompletableFuture<T> attempt(Supplier<CompletableFuture<T>> work, int retriesLeft) {
        return safe(work).exceptionallyCompose(error -> {
            if (retriesLeft <= 0) {
                return CompletableFuture.failedFuture(error);
            }
            return CompletableFuture.supplyAsync(() -> null,
                            CompletableFuture.delayedExecutor(Math.max(0, retryDelayMs.getAsInt()), TimeUnit.MILLISECONDS))
                    .thenCompose(x -> attempt(work, retriesLeft - 1));
        });
    }

    /** Runs {@code work}, turning a synchronous throw (database stopped) into a failed future. */
    private static <T> CompletableFuture<T> safe(Supplier<CompletableFuture<T>> work) {
        try {
            return work.get();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Copies for give-backs. */
    static List<BoxItem> items(List<BoxEntry> entries) {
        List<BoxItem> out = new ArrayList<>(entries.size());
        entries.forEach(e -> out.add(e.asItem()));
        return out;
    }
}

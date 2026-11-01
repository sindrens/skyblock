package net.skybornmc.skyblock.stacking;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ECON-SPAWNERS step 9: kills of tier 1-4 mobs per island (any source), for the unlock of the next spawner tier in /shop.
 * Memory first (main thread), written behind in one batch every {@code spawners.unlock.save-seconds}
 * ({@code INSERT ... ON DUPLICATE KEY UPDATE kills = kills + VALUES(kills)}), loaded lazily per island on first read. Only
 * mobs of the tier lists 1-4 are counted (no map growth). Never used for money.
 */
final class UnlockTracker {

    private static final String UPSERT = "INSERT INTO stacking_kill_counts (island_id, mob, kills, updated_at) VALUES (?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE kills = kills + VALUES(kills), updated_at = VALUES(updated_at)";

    private final Database db;
    private final Logger log;
    private final Predicate<String> counted;
    /** Island → mob → kills (database value + pending). Present once loaded. */
    private final Map<UUID, Map<String, Long>> kills = new HashMap<>();
    /** Island → mob → kills not written yet. */
    private final Map<UUID, Map<String, Long>> pending = new HashMap<>();
    private final Set<UUID> loading = new java.util.HashSet<>();
    private final java.util.concurrent.Executor main;

    UnlockTracker(Database db, Logger log, Predicate<String> counted, java.util.concurrent.Executor main) {
        this.db = db;
        this.log = log;
        this.counted = counted;
        this.main = main;
    }

    /** Counts {@code amount} kills of {@code mob} on {@code island} (main thread, O(1)). */
    void record(UUID island, String mob, long amount) {
        if (island == null || amount <= 0 || !counted.test(mob)) {
            return;
        }
        pending.computeIfAbsent(island, k -> new HashMap<>()).merge(mob, amount, Long::sum);
        Map<String, Long> known = kills.get(island);
        if (known != null) {
            known.merge(mob, amount, Long::sum);
        }
    }

    /** The island's kills per mob (empty while loading; a load is started on first use). Main thread. */
    Map<String, Long> kills(UUID island) {
        if (island == null) {
            return Map.of();
        }
        Map<String, Long> known = kills.get(island);
        if (known != null) {
            return known;
        }
        if (loading.add(island)) {
            db.async(c -> Sql.query(c, "SELECT mob, kills FROM stacking_kill_counts WHERE island_id = ?",
                    rs -> Map.entry(rs.getString("mob"), rs.getLong("kills")), island)).whenCompleteAsync((rows, error) -> {
                loading.remove(island);
                if (error != null) {
                    log.log(Level.WARNING, "Stacking: could not load the kill counts of island " + island, Database.cause(error));
                    return;
                }
                Map<String, Long> map = new HashMap<>();
                rows.forEach(e -> map.merge(e.getKey(), e.getValue(), Long::sum));
                Map<String, Long> waiting = pending.get(island);
                if (waiting != null) {
                    waiting.forEach((mob, n) -> map.merge(mob, n, Long::sum));
                }
                kills.put(island, map);
            }, main);
        }
        Map<String, Long> waiting = pending.get(island);
        return waiting == null ? Map.of() : Map.copyOf(waiting);
    }

    /** Writes every pending count in one batch (async). */
    CompletableFuture<Void> flush() {
        if (pending.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        long now = System.currentTimeMillis();
        List<Object[]> rows = new ArrayList<>();
        pending.forEach((island, mobs) -> mobs.forEach((mob, n) -> rows.add(new Object[]{island, mob, n, now})));
        Map<UUID, Map<String, Long>> sent = new HashMap<>(pending);
        pending.clear();
        return db.run(c -> Sql.batch(c, UPSERT, rows)).whenCompleteAsync((v, error) -> {
            if (error != null) {
                log.log(Level.WARNING, "Stacking: writing " + rows.size() + " kill count(s) failed; they are tried again", Database.cause(error));
                sent.forEach((island, mobs) -> mobs.forEach((mob, n) -> pending.computeIfAbsent(island, k -> new HashMap<>()).merge(mob, n, Long::sum)));
            }
        }, main);
    }

    /** Shutdown: writes what is pending, blocking. */
    void flushBlocking() {
        if (pending.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Object[]> rows = new ArrayList<>();
        pending.forEach((island, mobs) -> mobs.forEach((mob, n) -> rows.add(new Object[]{island, mob, n, now})));
        try {
            db.blocking(c -> Sql.batch(c, UPSERT, rows));
            pending.clear();
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Stacking: kill counts could not be saved at shutdown", e);
        }
    }

    /** Island disbanded or reset: its progress is gone (rows deleted). Returns the rows deleted. */
    CompletableFuture<Integer> clear(UUID island) {
        kills.remove(island);
        pending.remove(island);
        return db.async(c -> Sql.update(c, "DELETE FROM stacking_kill_counts WHERE island_id = ?", island));
    }

    /** Forgets a loaded island (unload), after its pending counts were queued. */
    void forget(UUID island) {
        if (!pending.containsKey(island)) {
            kills.remove(island);
        }
    }
}

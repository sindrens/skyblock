package net.skybornmc.skyblock.islands.stats;

import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The island stats service (M2.6). Recording only touches the in-memory {@link StatsBuffer}. Everything that reaches
 * the database runs through one serial queue, in order: flushes, admin resets, retention purges and reads. That gives:
 * <ul>
 *   <li><b>never counted twice:</b> a flush writes one batch in one transaction; when it fails nothing was written and
 *       the batch goes back into the buffer for the next flush;</li>
 *   <li><b>at most one flush interval lost on a crash</b> (the module flushes every {@code flush-seconds} and on
 *       shutdown);</li>
 *   <li><b>exact reads:</b> a snapshot copies the unwritten counters when it is asked for and reads the database after
 *       every flush that was already started (whose counters are no longer in the buffer), before any later one;</li>
 *   <li><b>clean resets:</b> a reset drops the island's unwritten counters and deletes its rows after earlier flushes.</li>
 * </ul>
 * Only one flush is in the queue at a time; a slow database makes the next flush wait (its counters stay buffered).
 */
public final class StatsTracker implements IslandStatsService {

    /** The island and member key a player/profile counts for, or null when they don't count (no island / not a member). */
    @FunctionalInterface
    public interface MemberResolver {
        Target resolve(UUID playerOrProfileKey);
    }

    public record Target(UUID island, UUID member) {
    }

    private final StatsStorage storage;
    private final StatsClock clock;
    private final Logger log;
    private final MemberResolver resolver;
    private final StatsBuffer buffer = new StatsBuffer();
    private final Set<IslandStat> sources = ConcurrentHashMap.newKeySet();
    private volatile boolean enabled = true;
    private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);
    private CompletableFuture<Integer> lastFlush = CompletableFuture.completedFuture(0);
    private boolean closed;

    public StatsTracker(StatsStorage storage, StatsClock clock, Logger log, MemberResolver resolver) {
        this.storage = storage;
        this.clock = clock;
        this.log = log;
        this.resolver = resolver;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public StatsClock clock() {
        return clock;
    }

    /** Counters waiting for the next flush. */
    public int pendingCounters() {
        return buffer.size();
    }

    // ------------------------------------------------------------------ recording (O(1), memory only)

    @Override
    public void record(UUID islandId, UUID memberKey, IslandStat stat, long amount) {
        if (!enabled || closed || islandId == null || memberKey == null || stat == null || amount <= 0) {
            return;
        }
        buffer.add(new StatKey(islandId, memberKey, stat, clock.today()), amount);
    }

    @Override
    public boolean recordFor(UUID playerOrProfileKey, IslandStat stat, long amount) {
        if (!enabled || playerOrProfileKey == null || stat == null || amount <= 0) {
            return false;
        }
        Target target = resolver.resolve(playerOrProfileKey);
        if (target == null) {
            return false;
        }
        record(target.island(), stat.perMember() ? target.member() : ISLAND_WIDE, stat, amount);
        return true;
    }

    @Override
    public void source(IslandStat stat) {
        if (stat != null) {
            sources.add(stat);
        }
    }

    @Override
    public boolean hasSource(IslandStat stat) {
        return sources.contains(stat);
    }

    @Override
    public int today() {
        return clock.today();
    }

    @Override
    public int weekStart() {
        return clock.weekStart();
    }

    // ------------------------------------------------------------------ the serial database queue

    private synchronized <T> CompletableFuture<T> enqueue(java.util.function.Supplier<CompletableFuture<T>> step) {
        CompletableFuture<T> next = tail.handle((ignored, error) -> null).thenCompose(v -> step.get());
        tail = next;
        return next;
    }

    /**
     * Writes everything buffered in one transaction (called every {@code flush-seconds}). Does nothing while the previous
     * flush is still running. Completes with the rows written; on failure the counters are back in the buffer.
     */
    public synchronized CompletableFuture<Integer> flush() {
        if (closed || !lastFlush.isDone()) {
            return CompletableFuture.completedFuture(0);
        }
        Map<StatKey, Long> drained = buffer.drain();
        if (drained.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        StatsBatch batch = StatsBatch.of(drained);
        CompletableFuture<Integer> written = tail.handle((ignored, error) -> null)
                .thenCompose(v -> storage.write(batch))
                .whenComplete((rows, error) -> {
                    if (error != null) {
                        buffer.mergeBack(drained);
                        log.log(Level.WARNING, "Island stats: writing " + drained.size() + " counters failed; retrying with the next flush",
                                unwrap(error));
                    }
                });
        tail = written;
        lastFlush = written;
        return written;
    }

    @Override
    public CompletableFuture<Snapshot> snapshot(UUID islandId) {
        synchronized (this) {
            Map<StatKey, Long> pending = buffer.copyOf(islandId);
            int today = clock.today();
            return enqueue(() -> storage.load(islandId, today))
                    .thenApply(rows -> SnapshotBuilder.build(islandId, today, rows.totals(), rows.daily(), rows.weekly(), pending));
        }
    }

    @Override
    public CompletableFuture<List<TopEntry>> top(IslandStat stat, Period period, Board board, int limit) {
        int today = clock.today();
        return enqueue(() -> storage.top(stat, period, board == null ? Board.ALL : board, today, limit));
    }

    /** Admin reset: drops the island's unwritten counters and deletes all its stored stats. Completes with rows removed. */
    public synchronized CompletableFuture<Integer> reset(UUID islandId) {
        buffer.discard(islandId);
        return enqueue(() -> storage.reset(islandId));
    }

    /** Deletes day buckets older than {@code dailyDays} days and week buckets older than {@code weeklyWeeks} weeks. */
    public CompletableFuture<Integer> purge(int dailyDays, int weeklyWeeks) {
        int today = clock.today();
        int minDay = minDay(today, dailyDays);
        int minWeek = minWeek(today, weeklyWeeks);
        return enqueue(() -> storage.purge(minDay, minWeek));
    }

    /** First day bucket kept when {@code days} days (today included) are kept. */
    public static int minDay(int today, int days) {
        return today - Math.max(1, days) + 1;
    }

    /** First week bucket kept when {@code weeks} weeks (this week included) are kept. */
    public static int minWeek(int today, int weeks) {
        return StatsClock.weekStart(today) - 7 * (Math.max(1, weeks) - 1);
    }

    /**
     * Shutdown: waits for queued work (up to {@code timeoutMillis}), then writes the rest of the buffer, blocking. Later
     * records are ignored. Returns the counters that could not be saved (0 when everything is in the database).
     */
    public int close(long timeoutMillis) {
        CompletableFuture<?> waitFor;
        synchronized (this) {
            closed = true;
            waitFor = tail;
        }
        try {
            waitFor.handle((ignored, error) -> null).get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.log(Level.WARNING, "Island stats: queued writes did not finish in " + timeoutMillis + " ms", e);
        }
        Map<StatKey, Long> rest = buffer.drain();
        if (rest.isEmpty()) {
            return 0;
        }
        try {
            storage.writeBlocking(StatsBatch.of(rest));
            return 0;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Island stats: " + rest.size() + " counters could not be saved on shutdown", unwrap(e));
            return rest.size();
        }
    }

    private static Throwable unwrap(Throwable t) {
        Throwable c = t;
        while ((c instanceof java.util.concurrent.CompletionException || c instanceof java.util.concurrent.ExecutionException)
                && c.getCause() != null) {
            c = c.getCause();
        }
        return c;
    }
}

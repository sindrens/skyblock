package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.util.Scheduler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads the funnel for the stats menu (TUTORIAL.md §10, TU7): reads the events of the period async, computes
 * {@link FunnelStats} off the main thread and caches each (days, client) result for {@code analytics.cache-seconds}.
 * Also deletes events older than {@code analytics.keep-days} (at start and daily).
 */
final class TutorialStats {

    private record Key(int days, FunnelStats.Client client) {
    }

    private record Cached(long at, CompletableFuture<FunnelStats.Result> result) {
    }

    private final TutorialModule module;
    private final Core core;
    private final Map<Key, Cached> cache = new ConcurrentHashMap<>();
    private Scheduler.Task purge;

    TutorialStats(TutorialModule module) {
        this.module = module;
        this.core = module.core();
    }

    /** The funnel of the last {@code days} days for these players (completes off the main thread). */
    CompletableFuture<FunnelStats.Result> funnel(int days, FunnelStats.Client client) {
        long now = System.currentTimeMillis();
        Key key = new Key(days, client);
        Cached c = cache.get(key);
        if (c != null && now - c.at() < module.config().statsCacheSeconds * 1000L && !c.result().isCompletedExceptionally()) {
            return c.result();
        }
        long since = now - days * 86_400_000L;
        List<String> steps = module.engine().flow().steps();
        long dropAfter = module.config().dropAfterHours * 3_600_000L;
        int limit = module.config().statsMaxRows;
        CompletableFuture<FunnelStats.Result> result = core.database().async(conn -> {
            List<FunnelStats.Event> events = module.store().events(conn, since, limit);
            Map<java.util.UUID, String> active = module.store().activeSteps(conn);
            return FunnelStats.compute(events, steps, active, client, now, dropAfter);
        });
        cache.put(key, new Cached(now, result));
        return result;
    }

    void clear() {
        cache.clear();
    }

    /** Starts the daily purge (first run shortly after start). */
    void startPurge() {
        stopPurge();
        purge = core.scheduler().repeat("tutorial-purge", 20L * 60, 20L * 60 * 60 * 24, this::purgeNow);
    }

    void stopPurge() {
        if (purge != null) {
            purge.cancel();
            purge = null;
        }
    }

    private void purgeNow() {
        long before = System.currentTimeMillis() - module.config().keepDays * 86_400_000L;
        core.database().async(conn -> module.store().purgeEvents(conn, before)).whenComplete((n, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Tutorial: deleting old events failed", Database.cause(e));
            } else if (n != null && n > 0) {
                int days = module.config().keepDays;
                core.plugin().getLogger().info("Tutorial: deleted " + n + " events older than " + days + " days.");
                core.scheduler().main("tutorial-purge-audit", () -> core.audit().log(net.skybornmc.skyblock.common.audit.AuditCategory.TUTORIAL,
                        "tutorial_events_purge", net.skybornmc.skyblock.common.audit.Actor.system("tutorial"),
                        Map.of("deleted", n, "keep_days", days)));
            }
        });
    }
}

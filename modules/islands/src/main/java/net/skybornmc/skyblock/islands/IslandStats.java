package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.islands.stats.IslandStatsConfig;
import net.skybornmc.skyblock.islands.stats.StatsClock;
import net.skybornmc.skyblock.islands.stats.StatsStore;
import net.skybornmc.skyblock.islands.stats.StatsTracker;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Island stats (M2.6): wires the {@link StatsTracker} (published as {@link IslandStatsService}) to the game: the event
 * listener ({@link IslandStatsListener}), the flush, playtime and retention tasks, {@code /is stats} and the stats menus.
 * Config {@code island-stats.yml}, texts {@code messages/islands.yml → stats}.
 */
final class IslandStats {

    /** Categories this module feeds itself; the others show "-" until their feature calls {@link IslandStatsService#source}. */
    static final List<IslandStat> OWN_SOURCES = List.of(IslandStat.BLOCKS_MINED, IslandStat.LOGS_CHOPPED, IslandStat.CROPS_HARVESTED,
            IslandStat.BLOCKS_PLACED, IslandStat.GENERATOR_OUTPUT, IslandStat.MOBS_KILLED, IslandStat.VISITORS, IslandStat.PLAYTIME);

    private static final long CLOSE_TIMEOUT_MS = 10_000;

    private final IslandsModule module;
    private final Core core;
    private final IslandManager islands;
    private final ModuleMessages msg;
    private final Executor main;
    private final IslandStatsConfig config = new IslandStatsConfig();
    private StatsTracker tracker;
    private IslandStatsListener listener;
    private Map<IslandStat, Material> icons = Map.of();
    private Scheduler.Task flushTask;
    private Scheduler.Task playtimeTask;
    private Scheduler.Task purgeTask;

    IslandStats(IslandsModule module, IslandManager islands) {
        this.module = module;
        this.core = module.core();
        this.islands = islands;
        this.msg = module.msg();
        this.main = new MainThread(core.plugin());
    }

    void enable() {
        loadConfig();
        tracker = new StatsTracker(new StatsStore(core.database()), StatsClock.system(config.zone()), core.plugin().getLogger(),
                this::resolve);
        tracker.enabled(config.enabled);
        OWN_SOURCES.forEach(tracker::source);
        listener = new IslandStatsListener(islands, tracker);
        listener.configure(config);
        Bukkit.getPluginManager().registerEvents(listener, core.plugin());
        islands.onRemoved(island -> {
            if (listener != null) {
                listener.forget(island.id());
            }
        });
        startTasks();
        core.services().register(IslandsModule.ID, IslandStatsService.class, tracker);
    }

    void reload() {
        loadConfig();
        if (tracker == null) {
            return;
        }
        tracker.enabled(config.enabled);
        listener.configure(config);
        startTasks();
    }

    /** Stops the tasks and writes everything still buffered (blocking, shutdown). */
    void disable() {
        cancelTasks();
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        if (tracker != null) {
            int lost = tracker.close(CLOSE_TIMEOUT_MS);
            if (lost > 0) {
                core.plugin().getLogger().severe("Island stats: " + lost + " counters were lost on shutdown (database unavailable).");
            }
        }
    }

    private void loadConfig() {
        core.configs().load(config);
        config.problems().forEach(core.plugin().getLogger()::warning);
        Map<IslandStat, Material> parsed = new EnumMap<>(IslandStat.class);
        config.iconKeys().forEach((stat, key) -> {
            Material material = Material.matchMaterial(key);
            if (material == null || !material.isItem() || material.isAir()) {
                core.plugin().getLogger().warning("island-stats.yml: menu.icons." + stat.key() + " '" + key + "' is not an item; paper is used");
            } else {
                parsed.put(stat, material);
            }
        });
        icons = Map.copyOf(parsed);
    }

    private void startTasks() {
        cancelTasks();
        long flushTicks = config.flushSeconds * 20L;
        // PERF2 ("island-stats-flush" 7 ms): the flush drained the buffer and built the three batch maps (one record per
        // counter, per table) on the main thread. The tracker is thread-safe (recording only touches the synchronized
        // buffer), so the whole flush now runs off the main thread; the database write was already async.
        StatsTracker flushing = tracker;
        flushTask = core.scheduler().asyncRepeat(flushTicks, flushTicks, () -> {
            try {
                flushing.flush();
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.WARNING, "Island stats: flush failed", e);
            }
        });
        long sampleTicks = config.playtimeSampleSeconds * 20L;
        int seconds = config.playtimeSampleSeconds;
        playtimeTask = core.scheduler().repeat("island-stats-playtime", sampleTicks, sampleTicks, () -> listener.samplePlaytime(seconds));
        long purgeTicks = config.purgeHours * 72_000L;
        purgeTask = core.scheduler().repeat("island-stats-purge", 1_200L, purgeTicks, () -> tracker.purge(config.dailyDays, config.weeklyWeeks)
                .whenComplete((removed, error) -> {
                    if (error != null) {
                        core.plugin().getLogger().log(Level.WARNING, "Island stats: deleting old buckets failed", Database.cause(error));
                    } else if (removed > 0) {
                        core.plugin().getLogger().info("Island stats: deleted " + removed + " old day/week rows.");
                    }
                }));
    }

    private void cancelTasks() {
        for (Scheduler.Task task : new Scheduler.Task[]{flushTask, playtimeTask, purgeTask}) {
            if (task != null) {
                task.cancel();
            }
        }
        flushTask = null;
        playtimeTask = null;
        purgeTask = null;
    }

    /** {@link IslandStatsService#recordFor}: the profile's island when it is a stored member (never coop/visitors). */
    private StatsTracker.Target resolve(UUID playerOrKey) {
        UUID key = islands.resolve(playerOrKey);
        return islands.islandOfPlayer(key).filter(island -> island.members().containsKey(key))
                .map(island -> new StatsTracker.Target(island.id(), key)).orElse(null);
    }

    // ------------------------------------------------------------------ shared with the menus and /isadmin

    StatsTracker tracker() {
        return tracker;
    }

    IslandStatsConfig config() {
        return config;
    }

    IslandsModule module() {
        return module;
    }

    IslandManager islands() {
        return islands;
    }

    Material icon(IslandStat stat) {
        return icons.getOrDefault(stat, Material.PAPER);
    }

    /** The category's shown name ({@code stats.category.<key>}). */
    String name(IslandStat stat) {
        return module.raw("stats.category." + stat.key());
    }

    /** A value in the category's unit: {@code 1,234}, {@code $1.2M} or {@code 2h 15m}. */
    static String value(IslandStat stat, long amount) {
        return switch (stat.unit()) {
            case COUNT -> Format.number(amount);
            case MONEY -> Format.money(amount);
            case SECONDS -> Format.duration(Duration.ofSeconds(amount));
        };
    }

    /** True when the category has a source or already has values (a source that was removed keeps its numbers visible). */
    boolean shown(IslandStat stat, IslandStatsService.Snapshot snapshot) {
        return tracker.hasSource(stat) || !snapshot.totals(stat).isZero();
    }

    // ------------------------------------------------------------------ /is stats

    /** {@code /is stats}: the stats menu of your own island. */
    void command(Player player) {
        Island island = islands.islandOfPlayer(islands.key(player)).orElse(null);
        if (island == null) {
            msg.send(player, "no-island");
            core.sounds().play(player, "error");
            return;
        }
        open(player, island, null, false);
    }

    /** Loads the island's stats, then opens the menu (as a child of {@code parent} when given). */
    void open(Player viewer, Island island, Menu parent, boolean staff) {
        load(viewer, island, snapshot -> {
            IslandStatsMenu menu = new IslandStatsMenu(this, island, snapshot, staff);
            if (parent != null && parent.isOpen() && parent.viewer() == viewer) {
                parent.openChild(menu);
            } else {
                core.menus().open(viewer, menu);
            }
        });
    }

    /** Loads a snapshot and hands it over on the main thread (only while the viewer is online); tells them on failure. */
    void load(Player viewer, Island island, java.util.function.Consumer<IslandStatsService.Snapshot> then) {
        CompletableFuture<IslandStatsService.Snapshot> future = tracker.snapshot(island.id());
        future.whenCompleteAsync((snapshot, error) -> {
            if (!viewer.isOnline()) {
                return;
            }
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Island stats: loading " + island.id() + " failed", Database.cause(error));
                msg.send(viewer, "stats.failed");
                core.sounds().play(viewer, "error");
                return;
            }
            then.accept(snapshot);
        }, main);
    }
}

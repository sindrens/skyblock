package net.skybornmc.skyblock.milestones;

import net.skybornmc.skyblock.common.progression.LevelSource;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Synergy (owner 2 Nov 2026: "island level without pillars, plan synergy"): every completed island milestone tier gives
 * {@code level.points-per-island-tier} island level points (added to the earned source {@code collections} under the key
 * {@code milestones}). Counts are read once at start (one grouped query) and kept in memory, so offline islands keep
 * their points; a newly completed tier adds one and asks for a level update.
 */
final class IslandLevelPoints implements Listener {

    static final String KEY = "milestones";

    private final MilestonesModule m;
    private final Map<UUID, Integer> tiers = new ConcurrentHashMap<>();

    IslandLevelPoints(MilestonesModule m) {
        this.m = m;
    }

    void start() {
        m.core().database().async(MilestoneStore::islandTierCounts).whenComplete((counts, error) -> {
            if (error != null) {
                m.core().plugin().getLogger().log(Level.WARNING, "Milestones: could not read island tier counts (no island level points yet)", error);
                return;
            }
            Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
                counts.forEach((island, n) -> tiers.merge(island, n, Math::max));
                progression().ifPresent(p -> counts.keySet().forEach(p::requestLevelUpdate));
            });
        });
        Bukkit.getPluginManager().registerEvents(this, m.core().plugin());
        progression().ifPresent(p -> p.levelPointsProvider(LevelSource.COLLECTIONS, KEY, this::points));
    }

    void stop() {
        HandlerList.unregisterAll(this);
        progression().ifPresent(p -> p.levelPointsProvider(LevelSource.COLLECTIONS, KEY, null));
    }

    /** Island level points of an island (main thread, memory only). */
    double points(UUID island) {
        return points(tiers.getOrDefault(island, 0), m.config().levelPointsPerIslandTier);
    }

    /** Pure: tiers × points per tier, never negative. */
    static double points(int tiers, double perTier) {
        return tiers <= 0 || !(perTier > 0) ? 0 : tiers * perTier;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTier(MilestoneTierCompletedEvent event) {
        if (!event.island()) {
            return;
        }
        tiers.merge(event.scopeId(), 1, Integer::sum);
        progression().ifPresent(p -> p.requestLevelUpdate(event.scopeId()));
    }

    private java.util.Optional<ProgressionService> progression() {
        return m.core().services().optional(ProgressionService.class);
    }
}

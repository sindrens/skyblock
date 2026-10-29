package net.skybornmc.skyblock.depths.tiers;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.depths.DepthsConfig;
import net.skybornmc.skyblock.depths.zones.Zone;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Remembers the "first" moments of each tier per player and season ({@code depths_tier_unlocks}): when it was unlocked
 * (audited, category world) and when it was first visited (title + sound once). Access itself always follows the live
 * skill level; this is history, not a gate.
 *
 * <p>Rows of online players are cached (loaded async on join, main-thread access only). Writes are queued in order on
 * one chain so two writes for the same row never race; a failed write is logged and the cache keeps the value (the
 * worst case is a repeated first-visit title after a restart).</p>
 */
final class TierProgress {

    /** One remembered tier. {@code visitedAt} is {@code null} until the first visit. */
    record Entry(long unlockedAt, Long visitedAt) {
    }

    private static final String SELECT = "SELECT zone, unlocked_at, first_visit_at FROM depths_tier_unlocks WHERE player = ? AND season_id = ?";
    private static final String TOUCH = "UPDATE depths_tier_unlocks SET first_visit_at = COALESCE(first_visit_at, ?) WHERE player = ? AND season_id = ? AND zone = ?";
    private static final String EXISTS = "SELECT 1 FROM depths_tier_unlocks WHERE player = ? AND season_id = ? AND zone = ?";
    private static final String INSERT = "INSERT INTO depths_tier_unlocks (player, season_id, zone, unlocked_at, first_visit_at) VALUES (?, ?, ?, ?, ?)";

    private final Core core;
    private final DepthsConfig config;
    private final Map<UUID, Map<String, Entry>> cache = new HashMap<>();
    /** Load generation per player: a quit/rejoin while a load runs drops the stale result. */
    private final Map<UUID, Integer> loading = new HashMap<>();
    private int generation;
    private CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

    TierProgress(Core core, DepthsConfig config) {
        this.core = core;
        this.config = config;
    }

    /** Loads a player's rows (async); {@link #loaded} turns true on the main thread when done. */
    void load(Player player) {
        UUID uuid = player.getUniqueId();
        int gen = ++generation;
        loading.put(uuid, gen);
        int season = config.tierSeasonId;
        chain.thenCompose(v -> core.database().async(c -> read(c, uuid, season)))
                .whenComplete((rows, error) -> core.scheduler().main("depths-tiers-load", () -> {
                    Integer current = loading.get(uuid);
                    if (current == null || current != gen || Bukkit.getPlayer(uuid) == null) {
                        return;
                    }
                    loading.remove(uuid);
                    if (error != null) {
                        core.plugin().getLogger().log(Level.WARNING, "Depths tiers: could not load unlocks of " + uuid
                                + " (first-visit titles are skipped until they rejoin)", error);
                        return;
                    }
                    cache.put(uuid, rows);
                }));
    }

    private static Map<String, Entry> read(Connection c, UUID uuid, int season) throws SQLException {
        Map<String, Entry> rows = new LinkedHashMap<>();
        for (Object[] row : Sql.query(c, SELECT, rs -> new Object[]{rs.getString("zone"), rs.getLong("unlocked_at"),
                Sql.nullableLong(rs, "first_visit_at")}, uuid, season)) {
            rows.put((String) row[0], new Entry((Long) row[1], (Long) row[2]));
        }
        return rows;
    }

    void unload(UUID uuid) {
        cache.remove(uuid);
        loading.remove(uuid);
    }

    boolean loaded(UUID uuid) {
        return cache.containsKey(uuid);
    }

    boolean unlocked(UUID uuid, String zone) {
        Map<String, Entry> rows = cache.get(uuid);
        return rows != null && rows.containsKey(zone);
    }

    boolean visited(UUID uuid, String zone) {
        Map<String, Entry> rows = cache.get(uuid);
        Entry entry = rows == null ? null : rows.get(zone);
        return entry != null && entry.visitedAt() != null;
    }

    /**
     * Remembers the first unlock of a tier (no-op if not loaded or already known) and audits it.
     *
     * @return true if this was the first unlock
     */
    boolean recordUnlock(Player player, Zone zone, int level) {
        Map<String, Entry> rows = cache.get(player.getUniqueId());
        if (rows == null || rows.containsKey(zone.id())) {
            return false;
        }
        long now = System.currentTimeMillis();
        rows.put(zone.id(), new Entry(now, null));
        write(player.getUniqueId(), zone.id(), now, null);
        audit(player, zone, level);
        return true;
    }

    /**
     * Remembers the first visit (and the unlock if it was not known yet).
     *
     * @param level the player's level for the audit row, or {@code null} when no skill system runs (no unlock audit)
     * @return true if this was the first visit
     */
    boolean recordVisit(Player player, Zone zone, Integer level) {
        Map<String, Entry> rows = cache.get(player.getUniqueId());
        if (rows == null) {
            return false;
        }
        Entry entry = rows.get(zone.id());
        if (entry != null && entry.visitedAt() != null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long unlockedAt = entry == null ? now : entry.unlockedAt();
        rows.put(zone.id(), new Entry(unlockedAt, now));
        write(player.getUniqueId(), zone.id(), unlockedAt, now);
        if (entry == null && level != null) {
            audit(player, zone, level);
        }
        return true;
    }

    private void audit(Player player, Zone zone, int level) {
        core.audit().logPlayer(AuditCategory.WORLD, "depths_tier_unlock", Actor.player(player), player.getUniqueId(),
                player.getName(), null, player.getLocation(), Map.of("zone", zone.id(), "aspect", zone.aspect().id(),
                        "tier", zone.tier(), "skill", zone.aspect().skill().id(), "required", zone.requiredLevel(),
                        "level", level, "season", config.tierSeasonId));
    }

    private void write(UUID uuid, String zone, long unlockedAt, Long visitedAt) {
        int season = config.tierSeasonId;
        chain = chain.thenCompose(v -> core.database().run(c -> upsert(c, uuid, season, zone, unlockedAt, visitedAt)))
                .exceptionally(error -> {
                    core.plugin().getLogger().log(Level.WARNING, "Depths tiers: could not save " + zone + " of " + uuid, error);
                    return null;
                });
    }

    static void upsert(Connection c, UUID uuid, int season, String zone, long unlockedAt, Long visitedAt) throws SQLException {
        boolean exists = visitedAt == null
                ? Sql.queryOne(c, EXISTS, rs -> Boolean.TRUE, uuid, season, zone).isPresent()
                : Sql.update(c, TOUCH, visitedAt, uuid, season, zone) > 0;
        if (!exists) {
            Sql.update(c, INSERT, uuid, season, zone, unlockedAt, visitedAt);
        }
    }

    /** Waits for queued writes (shutdown). */
    void flush() {
        try {
            chain.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            core.plugin().getLogger().log(Level.WARNING, "Depths tiers: queued unlock writes did not finish in 5 s", e);
        }
    }
}

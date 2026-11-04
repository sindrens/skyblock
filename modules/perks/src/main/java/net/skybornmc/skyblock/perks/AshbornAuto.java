package net.skybornmc.skyblock.perks;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ranks V2 (owner EP-Q15): Ashborn arrives by itself once the player has played
 * {@code freerank.min-playtime-minutes}. Checks run 100 ticks after a join and every
 * {@code freerank.auto.check-seconds} for at most {@code freerank.auto.batch} online players; they read memory only until a
 * claim is due. The claim is {@link FreeRankService#claim(Player, boolean)}: one row per account, then the group is ADDED
 * (never a downgrade; staff and donors keep their higher badge). {@code /freerank} stays as the one-time tag pick.
 */
final class AshbornAuto implements Listener {

    static final long JOIN_DELAY_TICKS = 100L;

    private final Core core;
    private final PerksConfig config;
    private final FreeRankService freeRank;
    /** Accounts that have their claim (or found one): never checked again this session. */
    private final Set<UUID> done = ConcurrentHashMap.newKeySet();
    /** Accounts whose claim row was checked and is absent (so the database is read once per session). */
    private final Set<UUID> noClaim = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();
    private final List<Scheduler.Task> tasks = new ArrayList<>();
    private int cursor;

    AshbornAuto(Core core, PerksConfig config, FreeRankService freeRank) {
        this.core = core;
        this.config = config;
        this.freeRank = freeRank;
    }

    void start() {
        long period = Math.max(1, config.freerankAutoCheckSeconds) * 20L;
        tasks.add(core.scheduler().repeat("ashborn-auto", period, period, this::sweep));
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
    }

    void stop() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        core.scheduler().later("ashborn-join", JOIN_DELAY_TICKS, () -> check(p));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        attempts.remove(id);
        noClaim.remove(id);
    }

    /** Every few seconds: the next {@code batch} online players not done yet. Main thread. */
    private void sweep() {
        if (!config.freerankAutoEnabled) {
            return;
        }
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.removeIf(p -> done.contains(p.getUniqueId()));
        if (online.isEmpty()) {
            return;
        }
        int batch = Math.max(1, config.freerankAutoBatch);
        for (int i = 0; i < Math.min(batch, online.size()); i++) {
            check(online.get(Math.floorMod(cursor++, online.size())));
        }
    }

    /** Main thread. Memory only until the claim is due; never throws. */
    void check(Player p) {
        try {
            if (p == null || !p.isOnline() || !config.freerankAutoEnabled) {
                return;
            }
            UUID id = p.getUniqueId();
            if (done.contains(id) || attempts.getOrDefault(id, 0) >= Math.max(1, config.freerankAutoMaxAttempts)) {
                return;
            }
            if (!FreeRankRules.due(config.freerankEnabled, core.ranks().available(), freeRank.tutorialComplete(p),
                    freeRank.playtimeOf(p), config.freerankMinPlaytimeMinutes, false)) {
                return;
            }
            if (!noClaim.contains(id)) {
                // Due by time: read the claim row once (players who used /freerank before are done).
                freeRank.claimed(id).whenComplete((has, error) -> core.scheduler().main("ashborn-loaded", () -> {
                    if (error != null) {
                        return;
                    }
                    if (Boolean.TRUE.equals(has)) {
                        done.add(id);
                    } else {
                        noClaim.add(id);
                        check(p);
                    }
                }));
                return;
            }
            freeRank.claim(p, true).thenAccept(outcome -> {
                switch (outcome) {
                    case CLAIMED, ALREADY -> done.add(id);
                    case RANK_FAILED -> {
                        int n = attempts.merge(id, 1, Integer::sum);
                        if (n == Math.max(1, config.freerankAutoMaxAttempts)) {
                            core.plugin().getLogger().warning("Ashborn: could not give the free rank to " + p.getName() + " after "
                                    + n + " tries; tried again at the next join.");
                        }
                    }
                    default -> { }
                }
            });
        } catch (RuntimeException e) {
            core.plugin().getLogger().warning("Ashborn: check failed for " + (p == null ? "?" : p.getName()) + ": " + e);
        }
    }
}

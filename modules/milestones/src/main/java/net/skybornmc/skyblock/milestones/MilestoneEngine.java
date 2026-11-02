package net.skybornmc.skyblock.milestones;

import net.skybornmc.skyblock.common.afk.AfkPool;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.depths.DepthsZones;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchEvent;
import net.skybornmc.skyblock.common.quest.GameActionEvent;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.common.world.PlacedBlocks;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Counts player actions toward milestone chains (Complete Developer Spec §7.3, §7.11). Only actions with an online acting
 * player in survival or adventure, outside the AFK pool, count; placed blocks never count; island chains count only for
 * members of the island where it happened. Nothing touches the database in an event: counts go to a {@link ProgressBuffer}
 * that is flushed every {@code flush-seconds}, on quit and on profile switch. Main thread.
 */
final class MilestoneEngine implements Listener {

    private final MilestonesModule m;
    private final ProgressBuffer buffer = new ProgressBuffer();
    private final PlacedBlocks placed;
    /** Online player -> tier notices waiting for the batch window to end. */
    private final Map<UUID, List<MilestoneStore.Completed>> waiting = new HashMap<>();
    private Scheduler.Task flushTask;
    private boolean flushing;

    MilestoneEngine(MilestonesModule module) {
        this.m = module;
        this.placed = new PlacedBlocks(module.config().placedTtlMinutes * 60_000L, module.config().placedMaxPerWorld);
    }

    void start() {
        long period = Math.max(2, m.config().flushSeconds) * 20L;
        flushTask = m.core().scheduler().repeat("milestones:flush", period, period, () -> {
            flush(buffer.drain());
            placed.prune(System.currentTimeMillis());
        });
    }

    void stop() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        Map<ProgressBuffer.Key, Long> rest = buffer.drain();
        if (!rest.isEmpty()) {
            try {
                m.core().database().blockingTransaction(c -> MilestoneStore.flush(c, rest, m.catalog()::chain, System.currentTimeMillis()));
            } catch (RuntimeException e) {
                m.core().plugin().getLogger().log(Level.WARNING, "Milestones: progress could not be saved at shutdown", e);
            }
        }
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        placed.placed(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        UUID world = b.getWorld().getUID();
        if (placed.isPlaced(world, b.getX(), b.getY(), b.getZ(), System.currentTimeMillis())) {
            placed.forget(world, b.getX(), b.getY(), b.getZ());
            return; // spec 7.3: placed blocks never count
        }
        count(event.getPlayer(), GameActionEvent.BREAK_BLOCK, b.getType().name(), b.getLocation(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller(); // the player dealt the last damage (traps, lava and grinders have no killer)
        if (killer != null && !(dead instanceof Player)) {
            count(killer, GameActionEvent.KILL_MOB, dead.getType().name(), dead.getLocation(), 1);
        }
    }

    /** Ripe harvests from the production module (target = the crop block, amount = crops). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAction(GameActionEvent event) {
        if (GameActionEvent.HARVEST_CROP.equals(event.action()) && event.target() != null) {
            // Fired only for members harvesting on their own island, so the player's position is on that island.
            count(event.getPlayer(), GameActionEvent.HARVEST_CROP, event.target(), event.getPlayer().getLocation(), event.amount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        m.claims().load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitched(net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent event) {
        m.claims().load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        flushProfile(event.getPlayer());
        waiting.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwitch(ProfileSwitchEvent event) {
        flushProfile(event.getPlayer());
    }

    private void flushProfile(Player p) {
        Optional<ProfileService> profiles = m.profiles();
        UUID profile = profiles.map(ps -> ps.profileKey(p)).orElse(p.getUniqueId());
        Map<ProgressBuffer.Key, Long> mine = buffer.drain(profile);
        if (!mine.isEmpty()) {
            flush(mine);
        }
    }

    // ------------------------------------------------------------------ counting

    /** Spec 7.3: who and where counts. */
    void count(Player p, String action, String target, Location at, long amount) {
        if (!m.config().enabled || p == null || !p.isOnline() || amount <= 0 || at == null || at.getWorld() == null) {
            return;
        }
        List<MilestoneModel.Chain> chains = m.catalog().chainsFor(action, target);
        if (chains.isEmpty()) {
            return;
        }
        GameMode mode = p.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return; // creative, spectator (staff mode) never count
        }
        if (m.core().services().optional(AfkPool.class).map(pool -> pool.inPool(p)).orElse(false)) {
            return;
        }
        Optional<ProfileService> profiles = m.profiles();
        if (profiles.map(ps -> ps.isSwitching(p.getUniqueId())).orElse(false)) {
            return;
        }
        UUID profile = profiles.map(ps -> ps.profileKey(p)).orElse(p.getUniqueId());
        boolean depths = m.core().services().optional(DepthsZones.class).map(z -> z.inDepths(at)).orElse(false);
        Optional<IslandService> islands = m.islands();
        UUID islandHere = depths ? null : islands.flatMap(i -> i.islandAt(at)).map(IslandService.IslandInfo::id).orElse(null);
        UUID ownIsland = islands.flatMap(i -> i.islandOf(p.getUniqueId())).map(IslandService.IslandInfo::id).orElse(null);
        for (MilestoneModel.Chain chain : chains) {
            boolean worldOk = switch (chain.world()) {
                case DEPTHS -> depths;
                case ISLAND -> islandHere != null;
                case ANY -> true;
            };
            if (!worldOk) {
                continue;
            }
            if (chain.menu() == MilestoneModel.Menu.ISLAND) {
                // Only members count, at the island where it happened (visitors and coop players never count).
                if (islandHere != null && islandHere.equals(ownIsland)) {
                    buffer.add(ProgressBuffer.Scope.ISLAND, islandHere, chain.id(), profile, amount);
                }
            } else if (islandHere == null) {
                // Personal: Depths and other non-island actions (an island-world action counts only in island chains).
                buffer.add(ProgressBuffer.Scope.PROFILE, profile, chain.id(), profile, amount);
            }
        }
    }

    /** Pending amount of a scope and chain (the menu adds it to the stored value). */
    long pending(ProgressBuffer.Scope scope, UUID scopeId, String chainId) {
        return buffer.pending(scope, scopeId, chainId);
    }

    // ------------------------------------------------------------------ flush and notices

    private void flush(Map<ProgressBuffer.Key, Long> batch) {
        if (batch.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        m.core().database().transaction(c -> MilestoneStore.flush(c, batch, m.catalog()::chain, now)).whenCompleteAsync((result, error) -> {
            if (error != null) {
                m.core().plugin().getLogger().log(Level.WARNING, "Milestones: saving progress failed; it is tried again at the next save",
                        Database.cause(error));
                buffer.putBack(batch);
                return;
            }
            result.discovered().forEach(this::discovered);
            result.completed().forEach(this::completed);
        }, m.main());
    }

    /** The online players a scope's notices go to: every online member of an island, or the profile's player. */
    private List<Player> receivers(ProgressBuffer.Scope scope, UUID scopeId) {
        List<Player> out = new ArrayList<>();
        if (scope == ProgressBuffer.Scope.PROFILE) {
            m.profiles().flatMap(ps -> ps.onlinePlayer(scopeId)).filter(p -> m.profiles().get().profileKey(p).equals(scopeId))
                    .ifPresent(out::add);
            if (m.profiles().isEmpty()) {
                Optional.ofNullable(Bukkit.getPlayer(scopeId)).ifPresent(out::add);
            }
            return out;
        }
        m.islands().ifPresent(i -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (i.islandOf(p.getUniqueId()).map(IslandService.IslandInfo::id).filter(scopeId::equals).isPresent()) {
                    out.add(p);
                }
            }
        });
        return out;
    }

    private void discovered(MilestoneStore.Discovered d) {
        if (!m.config().announceDiscovery) {
            return;
        }
        m.catalog().chain(d.chainId()).ifPresent(chain -> {
            for (Player p : receivers(d.scope(), d.scopeId())) {
                m.msg().send(p, "discovered", Msg.p("chain", chain.name()));
            }
        });
    }

    /** Spec 7.4: notices inside the batch window merge into one title and one chat line. */
    private void completed(MilestoneStore.Completed c) {
        Bukkit.getPluginManager().callEvent(new MilestoneTierCompletedEvent(c.scope() == ProgressBuffer.Scope.ISLAND, c.scopeId(),
                c.chainId(), c.tier()));
        for (Player p : receivers(c.scope(), c.scopeId())) {
            List<MilestoneStore.Completed> list = waiting.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>());
            list.add(c);
            if (list.size() == 1) {
                long delay = Math.max(1, m.config().batchSeconds) * 20L;
                m.core().scheduler().later("milestones:notice", m.config().batchSeconds <= 0 ? 1 : delay, () -> announce(p));
            }
        }
    }

    private void announce(Player p) {
        List<MilestoneStore.Completed> list = waiting.remove(p.getUniqueId());
        if (list == null || list.isEmpty() || !p.isOnline()) {
            return;
        }
        if (list.size() == 1) {
            MilestoneStore.Completed c = list.getFirst();
            String name = m.catalog().chain(c.chainId()).map(MilestoneModel.Chain::name).orElse(c.chainId());
            m.msg().send(p, "tier-ready.chat", Msg.p("chain", name), Msg.number("tier", c.tier()));
            m.msg().title(p, "tier-ready.title", Msg.p("chain", name), Msg.number("tier", c.tier()));
        } else {
            m.msg().send(p, "tiers-ready.chat", Msg.number("count", list.size()));
            m.msg().title(p, "tiers-ready.title", Msg.number("count", list.size()));
        }
        m.core().sounds().play(p, "unlock"); // ui.toast.challenge_complete (UI_STYLE sound table)
    }
}

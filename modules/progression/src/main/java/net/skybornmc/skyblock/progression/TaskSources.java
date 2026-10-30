package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.economy.BankDepositEvent;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.progression.TaskType;
import net.skybornmc.skyblock.common.sell.ItemsSoldEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Task progress from normal play (PHASE-5 M5.3). Anti-abuse is the same as the pillars: only counting members (never coop
 * players or visitors), never in creative/spectator, on islands only on their <b>own</b> island, blocks placed by players
 * count nothing ({@link PlacedBlocks}), kills only by the player's own hit or arrow (fall, lava and stack kills count 0).
 * Depths blocks come from {@link DepthsTaskListener}. Every handler is O(1) and runs at MONITOR after protection, so
 * cancelled actions never count; untracked targets return before any lookup.
 */
final class TaskSources implements Listener {

    private final Core core;
    private final TaskManager tasks;
    private final Map<UUID, PlacedBlocks> placed = new HashMap<>();
    private Set<Material> natural = EnumSet.noneOf(Material.class);
    private Set<String> killCauses = Set.of();
    private Set<String> excludedSell = Set.of();
    private int placedMemory = -1;

    TaskSources(Core core, TaskManager tasks) {
        this.core = core;
        this.tasks = tasks;
        configure();
    }

    void configure() {
        TasksConfig c = tasks.config();
        Set<Material> n = EnumSet.noneOf(Material.class);
        for (String id : c.naturalHarvest) {
            Material m = Material.matchMaterial(id);
            if (m == null || !m.isBlock()) {
                core.plugin().getLogger().warning("tasks-settings.yml → natural-harvest: '" + id + "' is not a block (skipped)");
            } else {
                n.add(m);
            }
        }
        natural = n;
        killCauses = PillarRules.upperSet(c.killCauses);
        excludedSell = PillarRules.lowerSet(c.excludedSellSources);
        if (placedMemory != c.placedMemory) {
            placed.clear();
            placedMemory = c.placedMemory;
        }
    }

    // ------------------------------------------------------------------ helpers

    private IslandService islands() {
        return core.services().optional(IslandService.class).orElse(null);
    }

    private static boolean playing(Player player) {
        GameMode mode = player.getGameMode();
        return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
    }

    /** The island an action of {@code player} at {@code location} counts for (null = it counts for nothing). */
    UUID countingIsland(Player player, Location location) {
        if (player == null || !playing(player)) {
            return null;
        }
        IslandService islands = islands();
        if (islands == null) {
            return null;
        }
        UUID own = islands.islandOf(player.getUniqueId()).map(IslandService.IslandInfo::id).orElse(null);
        if (own == null) {
            return null;
        }
        boolean inIslandWorld = location != null && location.getWorld() != null && islands.isIslandWorld(location.getWorld());
        UUID here = inIslandWorld ? islands.islandAt(location).map(IslandService.IslandInfo::id).orElse(null) : null;
        return TaskRules.countingIsland(own, islands.countsForIsland(own, player.getUniqueId()), inIslandWorld, here);
    }

    private UUID cause(Player player) {
        return ProfileService.of(core.services()).profileKey(player);
    }

    private void count(Player player, Location where, TaskType type, String object, long amount) {
        if (!tasks.enabled() || amount <= 0 || !tasks.tracks(type, object)) {
            return;
        }
        UUID island = countingIsland(player, where);
        if (island != null) {
            tasks.count(island, type, object, amount, cause(player));
        }
    }

    /** A Depths resource break (DepthsTaskListener): per-player blocks, never placed by players. */
    void countDepths(Player player, Location where, TaskType type, String object) {
        count(player, where, type, object, 1);
    }

    private static String id(Material m) {
        return m.getKey().getKey();
    }

    private PlacedBlocks placed(UUID island) {
        return placed.computeIfAbsent(island, k -> new PlacedBlocks(tasks.config().placedMemory));
    }

    /** The type a broken island block counts as, or null. */
    private TaskType typeOf(Material type, BlockData data) {
        if (Tag.LOGS.isTagged(type)) {
            return TaskType.CHOP;
        }
        if (natural.contains(type)) {
            return TaskType.FARM;
        }
        if (data instanceof Ageable) {
            return TaskType.FARM;
        }
        return TaskType.MINE;
    }

    // ------------------------------------------------------------------ island blocks

    /** Remembers placed blocks that a task reads, so breaking them again counts nothing. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!tasks.enabled()) {
            return;
        }
        Block block = event.getBlockPlaced();
        Material type = block.getType();
        TaskType kind = typeOf(type, block.getBlockData());
        if (kind == TaskType.FARM && !natural.contains(type)) {
            return; // ripe crops can't be placed ripe: the age check covers them
        }
        if (!tasks.tracks(kind, id(type))) {
            return;
        }
        IslandService islands = islands();
        if (islands == null || !islands.isIslandWorld(block.getWorld())) {
            return;
        }
        islands.islandInSlot(block.getLocation()).ifPresent(i -> placed(i.id()).placed(block.getX(), block.getY(), block.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!tasks.enabled()) {
            return;
        }
        Block block = event.getBlock();
        Material type = block.getType();
        BlockData data = block.getBlockData();
        TaskType kind = typeOf(type, data);
        String object = id(type);
        if (!tasks.tracks(kind, object)) {
            return;
        }
        IslandService islands = islands();
        if (islands == null || !islands.isIslandWorld(block.getWorld())) {
            return; // Depths blocks: DepthsTaskListener
        }
        UUID slot = islands.islandInSlot(block.getLocation()).map(IslandService.IslandInfo::id).orElse(null);
        boolean wasPlaced = slot != null && placed(slot).brokePlaced(block.getX(), block.getY(), block.getZ());
        if (wasPlaced) {
            return;
        }
        if (kind == TaskType.FARM && !natural.contains(type) && !ripe(data)) {
            return;
        }
        count(event.getPlayer(), block.getLocation(), kind, object, 1);
    }

    /** Right-click harvests (sweet berries, cave vines): count as farm when the block is a tracked crop. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        Block block = event.getHarvestedBlock();
        String object = id(block.getType());
        IslandService islands = islands();
        if (islands == null || !islands.isIslandWorld(block.getWorld())) {
            return;
        }
        count(event.getPlayer(), block.getLocation(), TaskType.FARM, object, 1);
    }

    private static boolean ripe(BlockData data) {
        return data instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge();
    }

    // ------------------------------------------------------------------ kills

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        if (!tasks.enabled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (entity instanceof Player || killer == null) {
            return;
        }
        String object = entity.getType().getKey().getKey();
        if (!tasks.tracks(TaskType.KILL, object)) {
            return;
        }
        EntityDamageEvent last = entity.getLastDamageCause();
        boolean byKiller = last instanceof EntityDamageByEntityEvent hit && killer.getUniqueId().equals(source(hit.getDamager()));
        if (!PillarRules.killCounts(last == null ? null : last.getCause().name(), byKiller, killCauses)) {
            return;
        }
        count(killer, entity.getLocation(), TaskType.KILL, object, 1);
    }

    private static UUID source(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter.getUniqueId();
        }
        return damager == null ? null : damager.getUniqueId();
    }

    // ------------------------------------------------------------------ crafting

    /** Crafted and smelted items (the vanilla statistic counts shift-click crafts exactly). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStatistic(PlayerStatisticIncrementEvent event) {
        if (event.getStatistic() != Statistic.CRAFT_ITEM || event.getMaterial() == null) {
            return;
        }
        long amount = (long) event.getNewValue() - event.getPreviousValue();
        count(event.getPlayer(), null, TaskType.CRAFT, id(event.getMaterial()), amount);
    }

    // ------------------------------------------------------------------ economy

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSold(ItemsSoldEvent event) {
        if (TaskRules.excludedSource(event.source(), excludedSell)) {
            return;
        }
        count(event.getPlayer(), null, TaskType.SELL, TaskType.ANY, event.items());
        count(event.getPlayer(), null, TaskType.EARN, TaskType.ANY, event.value());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeposit(BankDepositEvent event) {
        if (event.island()) {
            count(event.getPlayer(), null, TaskType.DEPOSIT, TaskType.ANY, event.amount());
        }
    }

    /** Drops the placed-block memory when the islands world unloads. */
    @EventHandler
    public void onUnload(WorldUnloadEvent event) {
        IslandService islands = islands();
        if (islands != null && islands.isIslandWorld(event.getWorld())) {
            placed.clear();
        }
    }
}

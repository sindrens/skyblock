package net.skybornmc.skyblock.worlds.protection;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.builds.BuildRegion;
import net.skybornmc.skyblock.common.region.FlagRegion;
import net.skybornmc.skyblock.common.region.RegionFlag;
import net.skybornmc.skyblock.common.region.RegionFlags;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.worlds.WorldsConfig;
import net.skybornmc.skyblock.worlds.builds.BuildPointsStore;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * World protection and region flags (DEPTHSPROT), published as {@link RegionFlags}. Flags come from
 * {@code worlds.yml → protection.*}: per world, per region kind of the applied builds ({@code build-points.yml}
 * regions), and per region handed in by modules ({@link #contribute}: the Depths ore/log fields and zones). The index
 * is rebuilt only when a build is applied, a module hands in regions or the config reloads; every event costs one
 * world map get when the world has no flags, else a chunk map get and a few box checks.
 *
 * <p>Protection flags ({@link RegionFlag#bypassable()}) don't apply to builders: creative mode plus
 * {@code protection.bypass-permission} or the world's own permission, or a build-mode hook (essentials {@code /build}).
 * Gameplay flags (PvE, targeting, pearls, wind charges) apply to everyone. Each denial shows one action bar line,
 * at most every {@code protection.message-cooldown-millis}. Listeners run at {@code LOW}, so the Depths engine (HIGH)
 * only sees breaks the flags allowed (ore/log fields, kind {@code field}).</p>
 */
public final class RegionProtection implements RegionFlags, Listener {

    private static final String MODULE = "worlds";
    private static final String CATEGORY = "regions";

    private final Core core;
    private final WorldsConfig config;
    private final BuildPointsStore store;
    private final ModuleMessages msg;

    private FlagRules rules = FlagRules.EMPTY;
    private BypassRule bypass = new BypassRule("", Map.of(), true);
    private BlockUse blockUse = new BlockUse(List.of(), List.of(), List.of());
    private volatile FlagIndex index = FlagIndex.EMPTY;
    private Map<String, BuildPointsStore.Applied> indexedFrom;
    private boolean dirty = true;

    private final Map<String, List<FlagRegion>> contributed = new LinkedHashMap<>();
    private final Map<String, BiPredicate<Player, String>> hooks = new LinkedHashMap<>();
    private final Map<UUID, Long> lastMessage = new HashMap<>();

    public RegionProtection(Core core, WorldsConfig config, BuildPointsStore store) {
        this.core = core;
        this.config = config;
        this.store = store;
        this.msg = core.messages(MODULE);
    }

    public void enable() {
        reload();
        core.plugin().getServer().getPluginManager().registerEvents(this, core.plugin());
        core.services().register(MODULE, RegionFlags.class, this);
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        lastMessage.clear();
    }

    /** Re-reads the settings and rebuilds the index. */
    public void reload() {
        java.util.function.Consumer<String> warn = problem -> core.plugin().getLogger().warning("worlds.yml → " + problem);
        rules = FlagRules.parse(config.protectionWorldFlags, config.protectionKindFlags, config.protectionRegionKinds, warn);
        bypass = BypassRule.parse(config.protectionBypassPermission, config.protectionWorldBypass, config.protectionBypassNeedsCreative, warn);
        blockUse = new BlockUse(config.protectionInteractAllowed, config.protectionContainers, config.protectionWorkstations);
        dirty = true;
        index();
    }

    /** The current index; rebuilt when build-points.yml, the contributions or the config changed. */
    FlagIndex index() {
        Map<String, BuildPointsStore.Applied> now = store.all();
        if (dirty || now != indexedFrom) {
            List<FlagIndex.Source> sources = new ArrayList<>();
            for (BuildPointsStore.Applied applied : now.values()) {
                for (BuildRegion r : applied.regions()) {
                    // Template builds (the starter island) are pasted per island elsewhere: their coordinates mean nothing here.
                    if (CATEGORY.equals(r.category()) && !config.templateWorlds.contains(r.world())) {
                        sources.add(new FlagIndex.Source(r.build(), new FlagRegion(r.id(), r.kind(), r.world(), r.box())));
                    }
                }
            }
            contributed.forEach((owner, list) -> list.forEach(r -> sources.add(new FlagIndex.Source(owner, r))));
            index = FlagIndex.build(rules, sources);
            indexedFrom = now;
            dirty = false;
        }
        return index;
    }

    // ------------------------------------------------------------------ RegionFlags

    @Override
    public boolean denied(String world, int x, int y, int z, RegionFlag flag) {
        return config.protectionEnabled && index().denied(world, x, y, z, flag);
    }

    @Override
    public List<String> regionsAt(String world, int x, int y, int z) {
        return index().regionsAt(world, x, y, z);
    }

    @Override
    public List<String> deniedAt(String world, int x, int y, int z) {
        return config.protectionEnabled ? index().deniedAt(world, x, y, z) : List.of();
    }

    @Override
    public void contribute(String owner, List<FlagRegion> regions) {
        if (regions == null || regions.isEmpty()) {
            contributed.remove(owner);
        } else {
            contributed.put(owner, List.copyOf(regions));
        }
        dirty = true;
    }

    @Override
    public void addBypass(String owner, BiPredicate<Player, String> hook) {
        hooks.put(owner, hook);
    }

    @Override
    public void removeBypass(String owner) {
        hooks.remove(owner);
    }

    @Override
    public boolean bypasses(Player player, String world) {
        if (bypass.bypasses(world, player.getGameMode() == GameMode.CREATIVE, player::hasPermission)) {
            return true;
        }
        for (BiPredicate<Player, String> hook : hooks.values()) {
            if (hook.test(player, world)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

    /** True if {@code flag} is denied at the block for this player (null = no player: no bypass). */
    private boolean blocks(Player player, String world, int x, int y, int z, RegionFlag flag) {
        if (!config.protectionEnabled) {
            return false;
        }
        FlagIndex idx = index();
        if (!idx.hasWorld(world) || !idx.denied(world, x, y, z, flag)) {
            return false;
        }
        return !(flag.bypassable() && player != null && bypasses(player, world));
    }

    private boolean blocks(Player player, Block block, RegionFlag flag) {
        return blocks(player, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), flag);
    }

    private boolean blocks(Player player, Location at, RegionFlag flag) {
        return at.getWorld() != null && blocks(player, at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ(), flag);
    }

    /** Cancels and tells the player (rate-limited). */
    private void deny(Cancellable event, Player player, RegionFlag flag) {
        event.setCancelled(true);
        tell(player, flag);
    }

    private void tell(Player player, RegionFlag flag) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(player.getUniqueId());
        if (last != null && now - last < config.protectionMessageCooldownMillis) {
            return;
        }
        lastMessage.put(player.getUniqueId(), now);
        boolean hint = flag.bypassable() && bypass.needsCreative() && player.getGameMode() != GameMode.CREATIVE
                && bypass.mayBypass(player.getWorld().getName(), player::hasPermission);
        msg.actionBar(player, hint ? "protection.bypass-hint" : "protection." + flag.id());
    }

    /** Blocks with a right-click action (stone, stairs... are not: clicking them never shows a denial line). */
    @SuppressWarnings("deprecation") // Paper marks it unreliable for a few blocks; good enough to skip plain ones
    private static boolean interactable(Material type) {
        return type.isInteractable();
    }

    /** The entity behind a hit: the shooter of a projectile, else the damager itself. */
    static Entity attacker(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            return source instanceof Entity e ? e : null;
        }
        return damager;
    }

    // ------------------------------------------------------------------ build

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (blocks(event.getPlayer(), event.getBlock(), RegionFlag.BUILD)) {
            deny(event, event.getPlayer(), RegionFlag.BUILD);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (blocks(event.getPlayer(), event.getBlockPlaced(), RegionFlag.BUILD)) {
            deny(event, event.getPlayer(), RegionFlag.BUILD);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (blocks(event.getPlayer(), event.getBlock(), RegionFlag.BUILD)) {
            deny(event, event.getPlayer(), RegionFlag.BUILD);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (blocks(event.getPlayer(), event.getBlockClicked(), RegionFlag.BUILD)) {
            deny(event, event.getPlayer(), RegionFlag.BUILD);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (blocks(event.getPlayer(), event.getBlock(), RegionFlag.BUILD)) {
            deny(event, event.getPlayer(), RegionFlag.BUILD);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onOpenSign(PlayerOpenSignEvent event) {
        if (event.getCause() != PlayerOpenSignEvent.Cause.PLACE && blocks(event.getPlayer(), event.getSign().getBlock(), RegionFlag.BUILD)) {
            deny(event, event.getPlayer(), RegionFlag.BUILD);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (blocks(event.getPlayer(), event.getBlock(), RegionFlag.BUILD)) {
            deny(event, event.getPlayer(), RegionFlag.BUILD);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (blocks(event.getPlayer(), event.getEntity().getLocation(), RegionFlag.BUILD)) {
            deny(event, event.getPlayer(), RegionFlag.BUILD);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (blocks(event.getPlayer(), event.getEntity().getLocation(), RegionFlag.BUILD)) {
            deny(event, event.getPlayer(), RegionFlag.BUILD);
        }
    }

    // ------------------------------------------------------------------ interact

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !config.protectionEnabled || !index().hasWorld(block.getWorld().getName())) {
            return;
        }
        Player player = event.getPlayer();
        Material type = block.getType();
        if (event.getAction() == Action.PHYSICAL) {
            if ((type == Material.FARMLAND || type == Material.TURTLE_EGG) && blocks(player, block, RegionFlag.BUILD)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        RegionFlag flag = switch (blockUse.classify(type.getKey().getKey())) {
            // Free blocks; but dye, glow ink or wax on a sign changes it.
            case ALLOWED -> Tag.ALL_SIGNS.isTagged(type) && event.getItem() != null && !event.getItem().getType().isAir()
                    && blocks(player, block, RegionFlag.BUILD) ? RegionFlag.BUILD : null;
            case CONTAINER -> blocks(player, block, RegionFlag.CONTAINERS) ? RegionFlag.CONTAINERS
                    : blocks(player, block, RegionFlag.INTERACT) ? RegionFlag.INTERACT : null;
            case WORKSTATION -> blocks(player, block, RegionFlag.WORKSTATIONS) ? RegionFlag.WORKSTATIONS
                    : blocks(player, block, RegionFlag.INTERACT) ? RegionFlag.INTERACT : null;
            case OTHER -> interactable(type) && blocks(player, block, RegionFlag.INTERACT) ? RegionFlag.INTERACT : null;
        };
        if (flag != null) {
            // Only the block's own action is refused: eating, throwing or placing a block still follow their own rules.
            event.setUseInteractedBlock(Event.Result.DENY);
            tell(player, flag);
        }
    }

    // ------------------------------------------------------------------ decoration entities

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity e = event.getRightClicked();
        if ((e instanceof ItemFrame || e instanceof ArmorStand) && blocks(event.getPlayer(), e.getLocation(), RegionFlag.ENTITY_EDIT)) {
            deny(event, event.getPlayer(), RegionFlag.ENTITY_EDIT);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (blocks(event.getPlayer(), event.getRightClicked().getLocation(), RegionFlag.ENTITY_EDIT)) {
            deny(event, event.getPlayer(), RegionFlag.ENTITY_EDIT);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        Player player = null;
        if (event instanceof HangingBreakByEntityEvent byEntity) {
            Entity remover = attacker(byEntity.getRemover());
            player = remover instanceof Player p ? p : null;
        } else if (event.getCause() != HangingBreakEvent.RemoveCause.EXPLOSION) {
            return; // its block was removed (physics): nothing to protect any more
        }
        if (blocks(player, event.getEntity().getLocation(), RegionFlag.ENTITY_EDIT)) {
            deny(event, player, RegionFlag.ENTITY_EDIT);
        }
    }

    // ------------------------------------------------------------------ damage, targets

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!config.protectionEnabled || !index().hasWorld(event.getEntity().getWorld().getName())) {
            return;
        }
        Entity victim = event.getEntity();
        Entity from = attacker(event.getDamager());
        Player player = from instanceof Player p ? p : null;
        if (victim instanceof Hanging || victim instanceof ArmorStand) {
            if (blocks(player, victim.getLocation(), RegionFlag.ENTITY_EDIT)) {
                deny(event, player, RegionFlag.ENTITY_EDIT);
            }
            return;
        }
        if (victim instanceof Player target) {
            if (from != null && !(from instanceof Player) && from != victim && blocks(null, target.getLocation(), RegionFlag.PVE_TAKEN)) {
                event.setCancelled(true);
                if (from instanceof Mob mob && mob.getTarget() == target) {
                    mob.setTarget(null);
                }
            }
            return;
        }
        if (player != null && victim instanceof Mob && blocks(player, player.getLocation(), RegionFlag.PVE_DEALT)) {
            deny(event, player, RegionFlag.PVE_DEALT);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player target && !(event.getEntity() instanceof Player)
                && blocks(null, target.getLocation(), RegionFlag.MOB_TARGET)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ pearls, wind charges

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLaunch(PlayerLaunchProjectileEvent event) {
        RegionFlag flag = event.getProjectile() instanceof EnderPearl ? RegionFlag.ENDER_PEARL
                : event.getProjectile() instanceof WindCharge ? RegionFlag.WIND_CHARGE : null;
        if (flag != null && blocks(event.getPlayer(), event.getPlayer().getLocation(), flag)) {
            deny(event, event.getPlayer(), flag);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPearlLand(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && blocks(event.getPlayer(), event.getTo(), RegionFlag.ENDER_PEARL)) {
            deny(event, event.getPlayer(), RegionFlag.ENDER_PEARL);
        }
    }

    // ------------------------------------------------------------------ fire, explosions

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (blocks(event.getPlayer(), event.getBlock(), RegionFlag.FIRE)) {
            deny(event, event.getPlayer(), RegionFlag.FIRE);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (blocks(null, event.getBlock(), RegionFlag.FIRE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        Material source = event.getSource().getType();
        if ((source == Material.FIRE || source == Material.SOUL_FIRE) && blocks(null, event.getBlock(), RegionFlag.FIRE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filter(event.getLocation(), event.blockList());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filter(event.getBlock().getLocation(), event.blockList());
    }

    private void filter(Location at, List<Block> blocks) {
        if (blocks.isEmpty() || at.getWorld() == null || !config.protectionEnabled || !index().hasWorld(at.getWorld().getName())) {
            return;
        }
        blocks.removeIf(b -> blocks(null, b, RegionFlag.EXPLOSIONS));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMessage.remove(event.getPlayer().getUniqueId());
    }
}

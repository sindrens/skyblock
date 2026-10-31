package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Golem;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.event.block.PlayerShearBlockEvent;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Island protection against players (M2.2). Inside an island every action needs the matching {@link IslandPerm} of the
 * player's role (Leader/Mod/Member/Coop, or visitor); nothing can be built outside a border; blocks can only be placed
 * or broken between {@code build.min-y} and {@code build.max-y} (owner decision: Y 0-256); PvP is off in the islands
 * world; a player inside a border can't be pushed or walk out of it. Staff with {@code skyblock.island.bypass} skip the
 * checks while {@code /is bypass} is on (logged). Denials show a rate-limited action-bar message.
 *
 * <p><b>Single decision:</b> {@link #check} (and {@link #allows}); every handler only maps its event to a location and
 * an {@link IslandPerm}. Lookups are O(1) through {@link Territory}; pickup/move handlers don't allocate.</p>
 *
 * <p>Cross-border rules for blocks and entities (pistons, liquids, explosions, growth, portals) live in
 * {@link BorderProtection}; void falls are handled here.</p>
 */
final class IslandProtection implements Listener {

    /**
     * Why an action was refused ({@link #NONE} = allowed). OWN_PROFILE: owner P3/P4, another profile of this player
     * belongs to the island, so nothing at all is allowed there.
     */
    enum Denial { NONE, OUTSIDE, HEIGHT, VISITOR, ROLE, OWN_PROFILE }

    private final IslandsModule module;
    private final IslandManager islands;
    private final Map<UUID, Long> lastMessage = new HashMap<>();
    private final Set<UUID> bypassing = new HashSet<>();
    private final Map<Material, IslandPerm> blockPerms = new EnumMap<>(Material.class);
    private final Map<Material, IslandPerm> itemPerms = new EnumMap<>(Material.class);
    private final Map<Material, IslandPerm> physicalPerms = new EnumMap<>(Material.class);

    IslandProtection(IslandsModule module, IslandManager islands) {
        this.module = module;
        this.islands = islands;
        for (Material material : Material.values()) {
            if (material.isLegacy()) {
                continue;
            }
            put(blockPerms, material, InteractRules.block(material.name()));
            put(itemPerms, material, InteractRules.item(material.name()));
            put(physicalPerms, material, InteractRules.physical(material.name()));
        }
    }

    private static void put(Map<Material, IslandPerm> map, Material material, IslandPerm perm) {
        if (perm != null) {
            map.put(material, perm);
        }
    }

    // ------------------------------------------------------------------ the decision

    /** True if {@code player} may do {@code perm} at {@code location}. Only the islands world is protected. */
    boolean allows(Player player, Location location, IslandPerm perm) {
        return check(player, location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), perm) == Denial.NONE;
    }

    /** The decision for one action at block x/y/z (no allocation). */
    Denial check(Player player, World world, int x, int y, int z, IslandPerm perm) {
        if (!islands.isIslandWorld(world) || bypassing(player)) {
            return Denial.NONE;
        }
        IslandsConfig config = module.config();
        if ((perm == IslandPerm.BREAK || perm == IslandPerm.PLACE) && (y < config.minBuildY || y > config.maxBuildY)) {
            return Denial.HEIGHT;
        }
        Island island = islands.islandAt(x, z);
        if (island == null) {
            return Denial.OUTSIDE;
        }
        UUID key = islands.key(player);
        PermRole role = islands.permRole(island, key);
        if (role == PermRole.VISITOR && islands.ownOtherProfileOn(island, key, null) != null) {
            // Visiting your own other profile's island must never move anything between your profiles.
            return Denial.OWN_PROFILE;
        }
        if (islands.rolePermissions().allows(island.perms(), role, perm)) {
            return Denial.NONE;
        }
        return role == PermRole.VISITOR ? Denial.VISITOR : Denial.ROLE;
    }

    private Denial check(Player player, Block block, IslandPerm perm) {
        return check(player, block.getWorld(), block.getX(), block.getY(), block.getZ(), perm);
    }

    private Denial check(Player player, Entity entity, IslandPerm perm) {
        return check(player, entity.getWorld(), Location.locToBlock(entity.getX()), Location.locToBlock(entity.getY()),
                Location.locToBlock(entity.getZ()), perm);
    }

    /** Cancels {@code event} and tells the player (rate-limited) when the decision is a denial. True if denied. */
    private boolean enforce(Player player, Denial denial, IslandPerm perm, Cancellable event, boolean tell) {
        if (denial == Denial.NONE) {
            return false;
        }
        event.setCancelled(true);
        if (tell) {
            deny(player, denial, perm);
        }
        return true;
    }

    private boolean enforce(Player player, Block block, IslandPerm perm, Cancellable event) {
        return enforce(player, check(player, block, perm), perm, event, true);
    }

    private boolean enforce(Player player, Entity entity, IslandPerm perm, Cancellable event) {
        return perm != null && enforce(player, check(player, entity, perm), perm, event, true);
    }

    /** Rate-limited action-bar message for a denial. */
    void deny(Player player, Denial denial, IslandPerm perm) {
        if (!ready(player)) {
            return;
        }
        switch (denial) {
            case OUTSIDE -> module.msg().actionBar(player, "protection.outside");
            case HEIGHT -> module.msg().actionBar(player, "protection.height", Msg.p("min", module.config().minBuildY),
                    Msg.p("max", module.config().maxBuildY));
            case VISITOR -> module.msg().actionBar(player, "protection.visitor", Msg.mm("action", action(perm)));
            case ROLE -> module.msg().actionBar(player, "protection.role", Msg.mm("action", action(perm)));
            case OWN_PROFILE -> module.msg().actionBar(player, "protection.own-profile");
            case NONE -> {
                return;
            }
        }
        module.core().sounds().play(player, "error");
    }

    /** A rate-limited action-bar message by key (PvP, portals). */
    void notice(Player player, String key) {
        if (ready(player)) {
            module.msg().actionBar(player, key);
            module.core().sounds().play(player, "error");
        }
    }

    private boolean ready(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(player.getUniqueId());
        if (last != null && now - last < module.config().protectionMessageCooldownMs) {
            return false;
        }
        lastMessage.put(player.getUniqueId(), now);
        return true;
    }

    private String action(IslandPerm perm) {
        return module.raw("protection.actions." + perm.key());
    }

    // ------------------------------------------------------------------ staff bypass (/is bypass)

    /** True if the bypass toggle is on and the player still has the permission. */
    boolean bypassing(Player player) {
        return !bypassing.isEmpty() && bypassing.contains(player.getUniqueId()) && player.hasPermission(IslandsModule.PERM_BYPASS);
    }

    /** {@code /is bypass}: toggles the staff bypass (logged). Off again on quit. */
    void toggleBypass(Player player) {
        boolean on = bypassing.add(player.getUniqueId());
        if (!on) {
            bypassing.remove(player.getUniqueId());
        }
        module.core().audit().log(AuditCategory.STAFF, on ? "island_bypass_on" : "island_bypass_off", Actor.player(player),
                null, null, player.getLocation(), Map.of("reason", "command"));
        module.msg().send(player, on ? "bypass.on" : "bypass.off");
        module.core().sounds().play(player, on ? "unlock" : "click");
    }

    /**
     * F15: staff mode switches the bypass for owner/admin (on when entering {@code /s}, off when leaving, logged with
     * reason {@code staff_mode}).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStaffMode(net.skybornmc.skyblock.common.staff.StaffModeEvent event) {
        Player player = event.getPlayer();
        StaffModeBypass.Change change = StaffModeBypass.decide(event.entered(),
                player.hasPermission(net.skybornmc.skyblock.common.staff.StaffModeEvent.ISLAND_CREATIVE_PERMISSION),
                player.hasPermission(IslandsModule.PERM_BYPASS), bypassing.contains(player.getUniqueId()));
        if (change == StaffModeBypass.Change.NONE) {
            return;
        }
        boolean on = change == StaffModeBypass.Change.TURN_ON;
        if (on) {
            bypassing.add(player.getUniqueId());
        } else {
            bypassing.remove(player.getUniqueId());
        }
        module.core().audit().log(AuditCategory.STAFF, on ? "island_bypass_on" : "island_bypass_off", Actor.player(player),
                null, null, player.getLocation(), Map.of("reason", "staff_mode"));
        if (player.isOnline()) {
            module.msg().send(player, on ? "bypass.staff-mode-on" : "bypass.staff-mode-off");
        }
    }

    // ------------------------------------------------------------------ blocks

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (enforce(event.getPlayer(), block, IslandPerm.BREAK, event)) {
            return;
        }
        if (blockPerms.get(block.getType()) == IslandPerm.SPAWNERS) {
            enforce(event.getPlayer(), block, IslandPerm.SPAWNERS, event);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (enforce(event.getPlayer(), event.getBlockPlaced(), IslandPerm.PLACE, event)) {
            return;
        }
        if (event instanceof BlockMultiPlaceEvent multi) {
            for (var state : multi.getReplacedBlockStates()) {
                Denial denial = check(event.getPlayer(), state.getWorld(), state.getX(), state.getY(), state.getZ(), IslandPerm.PLACE);
                if (enforce(event.getPlayer(), denial, IslandPerm.PLACE, event, true)) {
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        enforce(event.getPlayer(), event.getBlock(), IslandPerm.PLACE, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        enforce(event.getPlayer(), event.getBlock(), IslandPerm.BREAK, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        enforce(event.getPlayer(), event.getBlock(), IslandPerm.PLACE, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onOpenSign(PlayerOpenSignEvent event) {
        if (event.getCause() == PlayerOpenSignEvent.Cause.INTERACT) {
            enforce(event.getPlayer(), event.getSign().getBlock(), IslandPerm.PLACE, event);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (event.getPlayer() != null) {
            enforce(event.getPlayer(), event.getBlock(), IslandPerm.PLACE, event);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        enforce(event.getPlayer(), event.getHarvestedBlock(), IslandPerm.BREAK, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShearBlock(PlayerShearBlockEvent event) {
        enforce(event.getPlayer(), event.getBlock(), IslandPerm.BREAK, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLecternTake(PlayerTakeLecternBookEvent event) {
        enforce(event.getPlayer(), event.getLectern().getBlock(), IslandPerm.CONTAINERS, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() != null) {
            enforce(event.getPlayer(), event.getBlock(), IslandPerm.FIRE, event);
        }
    }

    /** Right-click blocks (containers, doors, redstone, item use on blocks), stepping on plates/farmland, punching fire. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !islands.isIslandWorld(block.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            IslandPerm perm = physicalPerms.get(block.getType());
            if (perm != null) {
                enforce(player, check(player, block, perm), perm, event, false); // stepping: silent, fires every tick
            }
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK) {
            // Sneaking with a block in hand places against the block instead of using it (BlockPlaceEvent decides).
            IslandPerm blockPerm = player.isSneaking() && event.isBlockInHand() ? null : blockPerms.get(block.getType());
            if (blockPerm != null && enforce(player, block, blockPerm, event)) {
                return;
            }
            IslandPerm itemPerm = event.hasItem() ? itemPerms.get(event.getMaterial()) : null;
            if (itemPerm != null) {
                enforce(player, block, itemPerm, event);
            }
            return;
        }
        if (action == Action.LEFT_CLICK_BLOCK) {
            Material type = block.getType();
            if (type == Material.DRAGON_EGG) {
                enforce(player, block, IslandPerm.BREAK, event);
                return;
            }
            if (event.getBlockFace() != null) {
                Block side = block.getRelative(event.getBlockFace());
                if (side.getType() == Material.FIRE || side.getType() == Material.SOUL_FIRE) {
                    enforce(player, side, IslandPerm.BREAK, event);
                }
            }
        }
    }

    // ------------------------------------------------------------------ entities

    /** The permission needed to use or hurt {@code entity}, or null (players, monsters: free). */
    static IslandPerm entityPerm(Entity entity, boolean sneaking) {
        if (entity instanceof Player) {
            return null;
        }
        if (entity instanceof AbstractVillager) {
            return IslandPerm.VILLAGERS;
        }
        if (entity instanceof LeashHitch) {
            return IslandPerm.ANIMALS;
        }
        if (entity instanceof ArmorStand || entity instanceof Hanging) {
            return IslandPerm.ARMOR_STANDS;
        }
        if (entity instanceof Minecart && entity instanceof InventoryHolder) {
            return IslandPerm.CONTAINERS;
        }
        if (entity instanceof ChestBoat) {
            return sneaking ? IslandPerm.CONTAINERS : IslandPerm.VEHICLES;
        }
        if (entity instanceof Vehicle && !(entity instanceof LivingEntity)) {
            return IslandPerm.VEHICLES;
        }
        if (entity instanceof Enemy) {
            return null;
        }
        if (entity instanceof Animals || entity instanceof WaterMob || entity instanceof Golem || entity instanceof Allay
                || entity instanceof Ambient) {
            return IslandPerm.ANIMALS;
        }
        return null;
    }

    /**
     * The permission needed to <i>hurt</i> {@code entity}: hostile mobs need {@link IslandPerm#HURT_MONSTERS} (ISL2, owner
     * #25), everything else the same permission as using it ({@link #entityPerm}).
     */
    static IslandPerm damagePerm(Entity entity) {
        if (entity instanceof Enemy && !(entity instanceof Player)) {
            return IslandPerm.HURT_MONSTERS;
        }
        return entityPerm(entity, false);
    }

    /** The player behind an attack (directly, a projectile, lit TNT or a lingering cloud), or null. */
    static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) {
            return player;
        }
        return null;
    }

    /** Right-click entities; also receives PlayerInteractAtEntityEvent (same handler list). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!islands.isIslandWorld(entity.getWorld())) {
            return;
        }
        enforce(event.getPlayer(), entity, entityPerm(entity, event.getPlayer().isSneaking()), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        enforce(event.getPlayer(), event.getRightClicked(), IslandPerm.ARMOR_STANDS, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!islands.isIslandWorld(victim.getWorld())) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (victim instanceof Player) {
            if (!module.config().pvp && victim != attacker) {
                event.setCancelled(true);
                notice(attacker, "protection.pvp");
            }
            return;
        }
        enforce(attacker, victim, damagePerm(victim), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!islands.isIslandWorld(victim.getWorld())) {
            return;
        }
        Player attacker = attacker(event.getCombuster());
        if (attacker == null || attacker == victim) {
            return;
        }
        if (victim instanceof Player) {
            if (!module.config().pvp) {
                event.setCancelled(true);
            }
            return;
        }
        IslandPerm perm = damagePerm(victim);
        if (perm != null) {
            enforce(attacker, check(attacker, victim, perm), perm, event, false); // fire aspect / flame: the hit already told them
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        if (module.config().pvp || !islands.isIslandWorld(potion.getWorld()) || !(potion.getShooter() instanceof Player thrower)
                || !harmful(potion.getEffects())) {
            return;
        }
        for (LivingEntity hit : event.getAffectedEntities()) {
            if (hit instanceof Player && hit != thrower) {
                event.setIntensity(hit, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        if (module.config().pvp || !islands.isIslandWorld(cloud.getWorld()) || !(cloud.getSource() instanceof Player thrower)) {
            return;
        }
        boolean harmful = harmful(cloud.getCustomEffects())
                || (cloud.getBasePotionType() != null && harmful(cloud.getBasePotionType().getPotionEffects()));
        if (harmful) {
            event.getAffectedEntities().removeIf(hit -> hit instanceof Player && hit != thrower);
        }
    }

    private static boolean harmful(Iterable<PotionEffect> effects) {
        for (PotionEffect effect : effects) {
            if (effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Entity caught = event.getCaught();
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY || caught == null || !islands.isIslandWorld(caught.getWorld())) {
            return;
        }
        if (caught instanceof Player) {
            if (!module.config().pvp && caught != event.getPlayer()) {
                event.setCancelled(true);
                notice(event.getPlayer(), "protection.pvp");
            }
            return;
        }
        enforce(event.getPlayer(), caught, damagePerm(caught), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShearEntity(PlayerShearEntityEvent event) {
        enforce(event.getPlayer(), event.getEntity(), IslandPerm.ANIMALS, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        IslandPerm perm = entityPerm(event.getEntity(), false);
        enforce(event.getPlayer(), event.getEntity(), perm == null ? IslandPerm.ANIMALS : perm, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        IslandPerm perm = entityPerm(event.getEntity(), false);
        enforce(event.getPlayer(), event.getEntity(), perm == null ? IslandPerm.ANIMALS : perm, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        enforce(event.getPlayer(), event.getEntity(), IslandPerm.ANIMALS, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Entity entity = event.getEntity();
        IslandPerm perm = entity instanceof ArmorStand ? IslandPerm.ARMOR_STANDS
                : entity instanceof Vehicle ? IslandPerm.VEHICLES : IslandPerm.PLACE;
        enforce(player, entity, perm, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() != null) {
            enforce(event.getPlayer(), event.getEntity(), IslandPerm.ARMOR_STANDS, event);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player remover = attacker(event.getRemover());
        if (remover != null) {
            IslandPerm perm = event.getEntity() instanceof LeashHitch ? IslandPerm.ANIMALS : IslandPerm.ARMOR_STANDS;
            enforce(remover, event.getEntity(), perm, event);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Player attacker = event.getAttacker() == null ? null : attacker(event.getAttacker());
        if (attacker != null && !(event.getVehicle() instanceof LivingEntity)) {
            enforce(attacker, event.getVehicle(), IslandPerm.VEHICLES, event);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Player attacker = event.getAttacker() == null ? null : attacker(event.getAttacker());
        if (attacker != null && !(event.getVehicle() instanceof LivingEntity)) {
            enforce(attacker, event.getVehicle(), IslandPerm.VEHICLES, event);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player) {
            Vehicle vehicle = event.getVehicle();
            enforce(player, vehicle, vehicle instanceof LivingEntity ? IslandPerm.ANIMALS : IslandPerm.VEHICLES, event);
        }
    }

    // ------------------------------------------------------------------ items

    /** Fires every tick while a player stands on items they can't take: silent and allocation-free. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            enforce(player, check(player, event.getItem(), IslandPerm.PICKUP), IslandPerm.PICKUP, event, false);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        enforce(event.getPlayer(), check(event.getPlayer(), event.getArrow(), IslandPerm.PICKUP), IslandPerm.PICKUP, event, false);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        enforce(event.getPlayer(), event.getPlayer(), IslandPerm.DROP, event);
    }

    // ------------------------------------------------------------------ teleports and movement

    /** Ender pearls and chorus fruit: the destination needs {@link IslandPerm#ENDER_PEARLS} (outside a border: never). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL && cause != PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT) {
            return;
        }
        Location to = event.getTo();
        Denial denial = check(event.getPlayer(), to.getWorld(), to.getBlockX(), to.getBlockY(), to.getBlockZ(), IslandPerm.ENDER_PEARLS);
        enforce(event.getPlayer(), denial, IslandPerm.ENDER_PEARLS, event, true);
    }

    /**
     * Keeps a player who is inside a border inside it (knockback, pistons, water, elytra; Java clients already collide
     * with the per-player border, this covers the rest). Only runs when the block x/z changes; no allocation unless the
     * move is refused.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!module.config().keepPlayersInside) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        int fx = from.getBlockX();
        int fz = from.getBlockZ();
        int tx = to.getBlockX();
        int tz = to.getBlockZ();
        if ((fx == tx && fz == tz) || !islands.isIslandWorld(to.getWorld())) {
            return;
        }
        Island island = islands.islandAt(fx, fz);
        if (island == null || island.contains(tx, tz)) {
            return;
        }
        Player player = event.getPlayer();
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE || bypassing(player)) {
            return;
        }
        Location back = from.clone();
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        event.setTo(back);
        player.setVelocity(player.getVelocity().zero());
        notice(player, "protection.border");
    }

    // ------------------------------------------------------------------ void falls

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVoidMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to.getY() >= module.config().voidY || event.getFrom().getY() < to.getY() || !islands.isIslandWorld(to.getWorld())) {
            return;
        }
        islands.rescueFromVoid(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID && event.getEntity() instanceof Player player
                && islands.isIslandWorld(player.getWorld())) {
            event.setCancelled(true);
            islands.rescueFromVoid(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        lastMessage.remove(player.getUniqueId());
        if (bypassing.remove(player.getUniqueId())) {
            module.core().audit().log(AuditCategory.STAFF, "island_bypass_off", Actor.player(player), null, null, player.getLocation(),
                    Map.of("reason", "quit"));
        }
    }

    /** Turns every bypass off (module disable). */
    void clearBypass() {
        bypassing.clear();
    }
}

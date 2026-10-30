package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M1.1 spawn protection for the worlds in {@code protection.worlds}: no breaking/placing, only whitelisted blocks
 * can be used, no damage, no hunger, no item drops, no natural mob spawning, no explosions; falling into the void
 * returns you to spawn. Builders with {@code skyblock.build.spawn} must switch on {@code /build} first, so they
 * don't break things by accident. Also {@code /setspawn} (admin).
 */
final class SpawnProtection extends CommandGroup implements Listener {

    private final JoinFlow join;
    private final Set<UUID> building = ConcurrentHashMap.newKeySet();

    SpawnProtection(Core core, EssentialsConfig config, ModuleMessages msg, JoinFlow join) {
        super(core, config, msg);
        this.join = join;
    }

    @Override
    void register() {
        core.commands().register(CommandSpec.command("build")
                .description("Toggle build mode in protected worlds (spawn)").category("Staff")
                .permission(Perms.BUILD_SPAWN).playerOnly()
                .executes(ctx -> toggleBuild(ctx.player())));
        core.commands().register(CommandSpec.command("setspawn")
                .description("Set the spawn point to where you stand").category("Admin")
                .permission(Perms.SETSPAWN).playerOnly()
                .executes(ctx -> setSpawn(ctx.player())));
    }

    // ------------------------------------------------------------------ commands

    private void toggleBuild(Player player) {
        boolean on = building.add(player.getUniqueId());
        if (!on) {
            building.remove(player.getUniqueId());
        }
        msg.send(player, on ? "build.on" : "build.off");
        audit(on ? "build_on" : "build_off", player, null, Map.of());
    }

    private void setSpawn(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        world.setSpawnLocation(loc);
        // Persist in worlds.yml (the worlds module applies it on every start).
        File file = new File(core.plugin().getDataFolder(), "worlds.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = "worlds." + world.getName() + ".spawn";
        boolean saved = false;
        if (yaml.contains("worlds." + world.getName())) {
            yaml.set(path + ".x", round(loc.getX()));
            yaml.set(path + ".y", round(loc.getY()));
            yaml.set(path + ".z", round(loc.getZ()));
            yaml.set(path + ".yaw", round(loc.getYaw()));
            try {
                yaml.save(file);
                saved = true;
            } catch (java.io.IOException e) {
                core.plugin().getLogger().warning("Could not save worlds.yml: " + e.getMessage());
            }
        }
        String pos = String.format(Locale.ROOT, "%.1f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ());
        msg.send(player, saved ? "setspawn.done" : "setspawn.live-only", Msg.p("world", world.getName()), Msg.p("position", pos));
        core.audit().log(AuditCategory.WORLD, "setspawn", Actor.player(player), world.getName(), null, loc,
                Map.of("x", round(loc.getX()), "y", round(loc.getY()), "z", round(loc.getZ()), "yaw", round(loc.getYaw())));
    }

    private static double round(double v) {
        return Math.round(v * 2) / 2.0;
    }

    // ------------------------------------------------------------------ helpers

    private boolean isProtected(World world) {
        return world != null && config.protectedWorlds.contains(world.getName());
    }

    /** True if this player may change the protected world right now. */
    private boolean canBuild(Player player) {
        return player.hasPermission(Perms.BUILD_SPAWN) && building.contains(player.getUniqueId());
    }

    /** True if the player has {@code /build} on and {@code world} is a protected world (region flags bypass hook). */
    boolean buildModeIn(Player player, String world) {
        return config.protectedWorlds.contains(world) && canBuild(player);
    }

    private void deny(Player player, Cancellable event) {
        event.setCancelled(true);
        if (player.hasPermission(Perms.BUILD_SPAWN)) {
            msg.actionBar(player, "build.hint");
        }
    }

    boolean interactAllowed(Material material) {
        String name = material.getKey().getKey();
        for (String pattern : config.interactAllowed) {
            String p = pattern.toLowerCase(Locale.ROOT);
            if (p.startsWith("*") ? name.endsWith(p.substring(1)) : p.endsWith("*") ? name.startsWith(p.substring(0, p.length() - 1)) : name.equals(p)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ events

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent event) {
        if (isProtected(event.getBlock().getWorld()) && !canBuild(event.getPlayer())) {
            deny(event.getPlayer(), event);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlace(BlockPlaceEvent event) {
        if (isProtected(event.getBlock().getWorld()) && !canBuild(event.getPlayer())) {
            deny(event.getPlayer(), event);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (isProtected(event.getBlock().getWorld()) && !canBuild(event.getPlayer())) {
            deny(event.getPlayer(), event);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isProtected(event.getBlock().getWorld()) && !canBuild(event.getPlayer())) {
            deny(event.getPlayer(), event);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !isProtected(block.getWorld()) || canBuild(event.getPlayer())) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            // Pressure plates are fine; trampling farmland and tripwires are not.
            if (block.getType() == Material.FARMLAND || block.getType() == Material.TRIPWIRE || block.getType() == Material.TURTLE_EGG) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !interactAllowed(block.getType())) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity e = event.getRightClicked();
        if (isProtected(e.getWorld()) && !canBuild(event.getPlayer())
                && (e instanceof org.bukkit.entity.ItemFrame || e instanceof org.bukkit.entity.ArmorStand)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (isProtected(event.getRightClicked().getWorld()) && !canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (isProtected(event.getEntity().getWorld()) && !(event.getRemover() instanceof Player p && canBuild(p))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent event) {
        if (!isProtected(event.getEntity().getWorld())) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                join.spawn().ifPresent(player::teleportAsync);
            }
            return;
        }
        // Protect decoration entities (armor stands, frames, display mobs) from players who aren't building.
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player p && !canBuild(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHunger(FoodLevelChangeEvent event) {
        if (isProtected(event.getEntity().getWorld()) && event.getFoodLevel() < event.getEntity().getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent event) {
        if (isProtected(event.getPlayer().getWorld()) && !canBuild(event.getPlayer())) {
            event.setCancelled(true); // the item stays in the inventory
            msg.actionBar(event.getPlayer(), "protection.no-drop");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!isProtected(event.getLocation().getWorld())) {
            return;
        }
        switch (event.getSpawnReason()) {
            case NATURAL, SPAWNER, JOCKEY, MOUNT, PATROL, RAID, REINFORCEMENTS, VILLAGE_INVASION, TRAP, SILVERFISH_BLOCK,
                 ENDER_PEARL, LIGHTNING -> event.setCancelled(true);
            default -> {
                // custom / command / plugin spawns (NPCs, display mobs) are allowed
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (isProtected(event.getLocation().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo().getY() < config.voidY && event.getFrom().getY() >= event.getTo().getY()
                && isProtected(event.getTo().getWorld())) {
            Player player = event.getPlayer();
            join.spawn().ifPresent(loc -> {
                player.setFallDistance(0);
                player.teleportAsync(loc);
                msg.actionBar(player, "protection.void");
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        building.remove(event.getPlayer().getUniqueId());
    }
}

package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.util.Stagger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Explosive;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cross-border protection (M2.2): nothing that isn't a player can carry a change over an island border or into the void
 * between slots. Rule: a change from block S to block T is allowed only if both are inside the same island's border
 * ({@link Territory#sameIsland}). Covers liquids, pistons, falling blocks, explosions (TNT, creepers, crystals, beds,
 * respawn anchors), tree/mushroom growth, spreading (fire, grass, vines, sculk …), forming, burning, dispensers;
 * nether/end portals are off in the islands world. Entities pushed out of a border (knockback, pistons, water) are put
 * back by a light periodic sweep of the chunk ring right outside occupied islands (borders are chunk-aligned).
 *
 * <p>Performance: liquid flow and growth are the hot events; they do two shifts, one array read and a few integer
 * compares, no allocation (no {@code getToBlock()}, no Location). BlockPhysicsEvent is deliberately not used.</p>
 */
final class BorderProtection implements Listener {

    private final IslandsModule module;
    private final IslandManager islands;
    private final Territory territory;
    private final IslandProtection players;
    private final Set<Island> sweepScratch = new HashSet<>();
    private long sweepTick;
    private final Location scratch = new Location(null, 0, 0, 0);

    BorderProtection(IslandsModule module, IslandManager islands, IslandProtection players) {
        this.module = module;
        this.islands = islands;
        this.territory = islands.territory();
        this.players = players;
    }

    private boolean islandWorld(World world) {
        return islands.isIslandWorld(world);
    }

    private boolean same(Block source, Block target) {
        return territory.sameIsland(source.getX(), source.getZ(), target.getX(), target.getZ());
    }

    private boolean insideAny(Block block) {
        return territory.at(block.getX(), block.getZ()) != null;
    }

    // ------------------------------------------------------------------ liquids, growth, spreading (hot)

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block from = event.getBlock();
        if (!islandWorld(from.getWorld())) {
            return;
        }
        BlockFace face = event.getFace();
        int x = from.getX();
        int z = from.getZ();
        if (!territory.sameIsland(x, z, x + face.getModX(), z + face.getModZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (islandWorld(event.getBlock().getWorld()) && !same(event.getSource(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        if (islandWorld(block.getWorld()) && !insideAny(block)) {
            event.setCancelled(true);
        }
    }

    /** Snow, ice, obsidian/cobblestone, concrete, frost walker, snow golem trails: only inside a border. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        Block block = event.getBlock();
        if (!islandWorld(block.getWorld())) {
            return;
        }
        if (!insideAny(block)) {
            event.setCancelled(true);
        } else if (event instanceof EntityBlockFormEvent byEntity && byEntity.getEntity() instanceof Player player
                && players.check(player, block.getWorld(), block.getX(), block.getY(), block.getZ(), IslandPerm.PLACE)
                != IslandProtection.Denial.NONE) {
            event.setCancelled(true); // frost walker of a visitor
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        Location at = event.getLocation();
        if (!islandWorld(at.getWorld())) {
            return;
        }
        Island island = territory.at(at.getBlockX(), at.getBlockZ());
        if (island == null) {
            event.setCancelled(true);
            return;
        }
        event.getBlocks().removeIf(state -> !island.contains(state.getX(), state.getZ()));
    }

    // ------------------------------------------------------------------ fire

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        if (event.getPlayer() != null || !islandWorld(block.getWorld())) {
            return; // players: IslandProtection (fire permission)
        }
        Block source = event.getIgnitingBlock();
        Entity entity = event.getIgnitingEntity();
        boolean ok;
        if (source != null) {
            ok = same(source, block);
        } else if (entity != null) {
            ok = territory.sameIsland(Location.locToBlock(entity.getX()), Location.locToBlock(entity.getZ()), block.getX(), block.getZ());
        } else {
            ok = insideAny(block);
        }
        if (!ok) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        if (!islandWorld(block.getWorld())) {
            return;
        }
        Block source = event.getIgnitingBlock();
        if (source == null ? !insideAny(block) : !same(source, block)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ pistons

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!piston(event.getBlock(), event.getBlocks(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!piston(event.getBlock(), event.getBlocks(), false)) {
            event.setCancelled(true);
        }
    }

    /** Every moved (or broken) block and its destination, plus the head when extending, stay in the piston's island. */
    private boolean piston(Block piston, List<Block> moved, boolean extending) {
        if (!islandWorld(piston.getWorld())) {
            return true;
        }
        Island island = territory.at(piston.getX(), piston.getZ());
        if (island == null) {
            return false;
        }
        BlockData data = piston.getBlockData();
        if (!(data instanceof Directional directional)) {
            return true;
        }
        BlockFace facing = directional.getFacing();
        int dx = extending ? facing.getModX() : -facing.getModX();
        int dz = extending ? facing.getModZ() : -facing.getModZ();
        if (extending && !island.contains(piston.getX() + facing.getModX(), piston.getZ() + facing.getModZ())) {
            return false;
        }
        for (Block block : moved) {
            int x = block.getX();
            int z = block.getZ();
            if (!island.contains(x, z) || !island.contains(x + dx, z + dz)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ explosions

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (islandWorld(entity.getWorld())) {
            keepInside(territory.at(Location.locToBlock(entity.getX()), Location.locToBlock(entity.getZ())), event.blockList());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Block block = event.getBlock();
        if (islandWorld(block.getWorld())) {
            keepInside(territory.at(block.getX(), block.getZ()), event.blockList());
        }
    }

    private static void keepInside(Island island, List<Block> blocks) {
        if (island == null) {
            blocks.clear();
            return;
        }
        blocks.removeIf(block -> !island.contains(block.getX(), block.getZ()));
    }

    /** Explosions (and fireworks) can't hurt entities in another island or outside the exploding island's border. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Explosive || damager instanceof Creeper || damager instanceof EnderCrystal
                || damager instanceof Firework || damager instanceof TNTPrimed)) {
            return;
        }
        Entity victim = event.getEntity();
        if (islandWorld(victim.getWorld()) && !sameIsland(damager, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplosionDamage(EntityDamageByBlockEvent event) {
        Block damager = event.getDamager();
        Entity victim = event.getEntity();
        if (damager == null || event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || !islandWorld(victim.getWorld())) {
            return;
        }
        if (!territory.sameIsland(damager.getX(), damager.getZ(), Location.locToBlock(victim.getX()), Location.locToBlock(victim.getZ()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingExplode(HangingBreakByEntityEvent event) {
        Entity remover = event.getRemover();
        if (remover == null || IslandProtection.attacker(remover) != null || !islandWorld(event.getEntity().getWorld())) {
            return; // players: IslandProtection
        }
        if (!sameIsland(remover, event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private boolean sameIsland(Entity a, Entity b) {
        return territory.sameIsland(Location.locToBlock(a.getX()), Location.locToBlock(a.getZ()),
                Location.locToBlock(b.getX()), Location.locToBlock(b.getZ()));
    }

    // ------------------------------------------------------------------ falling blocks and other block changers

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        Block block = event.getBlock();
        if (entity instanceof Player || !islandWorld(block.getWorld())) {
            return;
        }
        Island island = territory.at(block.getX(), block.getZ());
        if (island == null) {
            event.setCancelled(true);
            return;
        }
        if (entity instanceof FallingBlock falling) {
            Location origin = falling.getOrigin();
            if (origin != null && !island.contains(origin.getBlockX(), origin.getBlockZ())) {
                event.setCancelled(true); // landed in another island than it started from
            }
        }
    }

    // ------------------------------------------------------------------ dispensers

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block block = event.getBlock();
        if (!islandWorld(block.getWorld())) {
            return;
        }
        if (block.getBlockData() instanceof Directional directional) {
            BlockFace facing = directional.getFacing();
            if (!territory.sameIsland(block.getX(), block.getZ(), block.getX() + facing.getModX(), block.getZ() + facing.getModZ())) {
                event.setCancelled(true);
            }
        }
    }

    // ------------------------------------------------------------------ portals

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (module.config().disablePortals && islandWorld(event.getWorld())) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Player player) {
                players.notice(player, "protection.portal");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (module.config().disablePortals && islandWorld(event.getFrom().getWorld())) {
            event.setCancelled(true);
            players.notice(event.getPlayer(), "protection.portal");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (module.config().disablePortals && islandWorld(event.getFrom().getWorld())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ entities pushed out

    /**
     * One sweep slice (main thread, every tick; each island once per {@code protection.return-entities.interval-ticks}): for every island with a player
     * in its slot, the loaded chunks in the ring right outside its border are checked and entities found there are put
     * back inside. Chunks are never loaded by this.
     */
    void sweep() {
        if (!module.config().returnEntities) {
            return;
        }
        World world = Bukkit.getWorld(module.config().world);
        if (world == null) {
            return;
        }
        // PERF2 (P-25): called every tick; each occupied island is swept once per interval in its own tick slot, so the
        // chunk entity reads are spread over the interval instead of all landing on one tick.
        long tick = ++sweepTick;
        int period = Math.max(1, module.config().returnEntitiesIntervalTicks);
        sweepScratch.clear();
        for (Player player : world.getPlayers()) {
            Island island = islands.islandInSlotAt(Location.locToBlock(player.getX()), Location.locToBlock(player.getZ()));
            if (island != null && Stagger.due(island.id(), tick, period) && sweepScratch.add(island)) {
                sweep(world, island);
            }
        }
        sweepScratch.clear();
    }

    private void sweep(World world, Island island) {
        int ring = module.config().returnEntitiesRingChunks;
        int innerMinX = island.minX() >> 4;
        int innerMinZ = island.minZ() >> 4;
        int innerMaxX = island.maxX() >> 4;
        int innerMaxZ = island.maxZ() >> 4;
        int slotMinX = IslandGrid.slotMin(island.gridX()) >> 4;
        int slotMinZ = IslandGrid.slotMin(island.gridZ()) >> 4;
        int slotMaxX = slotMinX + IslandGrid.SLOT_SIZE / IslandGrid.CHUNK - 1;
        int slotMaxZ = slotMinZ + IslandGrid.SLOT_SIZE / IslandGrid.CHUNK - 1;
        for (int cx = Math.max(slotMinX, innerMinX - ring); cx <= Math.min(slotMaxX, innerMaxX + ring); cx++) {
            for (int cz = Math.max(slotMinZ, innerMinZ - ring); cz <= Math.min(slotMaxZ, innerMaxZ + ring); cz++) {
                boolean inner = cx >= innerMinX && cx <= innerMaxX && cz >= innerMinZ && cz <= innerMaxZ;
                if (inner || !world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                for (Entity entity : world.getChunkAt(cx, cz).getEntities()) {
                    if (returnable(entity)) {
                        putBack(island, entity);
                    }
                }
            }
        }
    }

    /** Mobs, animals, items, xp, empty vehicles, falling blocks and TNT; never players, projectiles, displays or hangings. */
    static boolean returnable(Entity entity) {
        // Riders move with their vehicle; a vehicle with riders (e.g. a player in a boat) is left alone (the player's own
        // move check and void rescue handle players).
        if (entity instanceof Player || !entity.isValid() || entity.getVehicle() != null || !entity.getPassengers().isEmpty()) {
            return false;
        }
        return entity instanceof LivingEntity || entity instanceof Item || entity instanceof ExperienceOrb
                || entity instanceof Vehicle || entity instanceof FallingBlock || entity instanceof TNTPrimed;
    }

    private void putBack(Island island, Entity entity) {
        double margin = module.config().returnEntitiesMargin;
        entity.getLocation(scratch);
        double x = Territory.clamp(scratch.getX(), island.minX(), island.maxX(), margin);
        double z = Territory.clamp(scratch.getZ(), island.minZ(), island.maxZ(), margin);
        if (x == scratch.getX() && z == scratch.getZ()) {
            return;
        }
        Location target = scratch.clone();
        target.setX(x);
        target.setZ(z);
        entity.setVelocity(entity.getVelocity().zero());
        entity.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }
}

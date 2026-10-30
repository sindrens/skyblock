package net.skybornmc.skyblock.islands;

import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the world-side {@code /is settings} of every island (ISL2): natural mob and animal spawning, fire spread,
 * explosions breaking blocks, leaf decay and crop trampling. Each check is one O(1) territory lookup and one array read;
 * nothing allocates. (Visitor pickup is the matrix permission, notifications are in {@link VisitManager}, visitor fly in
 * {@link IslandFly}, weather and time in {@link IslandEnvironment}.)
 *
 * <p>The islands world needs natural spawning and fire spread enabled in worlds.yml for the "on" values to do anything;
 * this listener then switches them off per island.</p>
 */
final class IslandFlagListener implements Listener {

    /** Spawn reasons that count as "natural" (spawners, eggs, breeding and commands always work). */
    static final Set<SpawnReason> NATURAL = EnumSet.of(SpawnReason.NATURAL, SpawnReason.REINFORCEMENTS,
            SpawnReason.PATROL, SpawnReason.TRAP, SpawnReason.JOCKEY, SpawnReason.MOUNT, SpawnReason.VILLAGE_INVASION);

    private final IslandManager islands;
    /** Per entity type: the setting that decides its natural spawning (hostile → mob-spawning, others → animal-spawning). */
    private final Map<EntityType, IslandFlag> spawnFlags = new EnumMap<>(EntityType.class);

    IslandFlagListener(IslandManager islands) {
        this.islands = islands;
        for (EntityType type : EntityType.values()) {
            Class<? extends Entity> c = type.getEntityClass();
            if (c != null && type.isAlive()) {
                spawnFlags.put(type, Enemy.class.isAssignableFrom(c) ? IslandFlag.MOB_SPAWNING : IslandFlag.ANIMAL_SPAWNING);
            }
        }
    }

    private Island islandAt(Block block) {
        return islands.isIslandWorld(block.getWorld()) ? islands.islandAt(block.getX(), block.getZ()) : null;
    }

    /** True if the block is in the islands world but its island has {@code flag} off (or it is outside every border). */
    private boolean off(Block block, IslandFlag flag) {
        if (!islands.isIslandWorld(block.getWorld())) {
            return false;
        }
        Island island = islands.islandAt(block.getX(), block.getZ());
        return island == null || !islands.flagOn(island, flag);
    }

    // ------------------------------------------------------------------ spawning

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPreSpawn(PreCreatureSpawnEvent event) {
        if (!NATURAL.contains(event.getReason())) {
            return;
        }
        Location at = event.getSpawnLocation();
        if (!islands.isIslandWorld(at.getWorld())) {
            return;
        }
        IslandFlag flag = spawnFlags.get(event.getType());
        if (flag == null) {
            return;
        }
        Island island = islands.islandAt(at.getBlockX(), at.getBlockZ());
        if (island == null || !islands.flagOn(island, flag)) {
            event.setCancelled(true);
            event.setShouldAbortSpawn(true);
        }
    }

    // ------------------------------------------------------------------ fire

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        Material type = event.getSource().getType();
        if ((type == Material.FIRE || type == Material.SOUL_FIRE) && off(event.getBlock(), IslandFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (off(event.getBlock(), IslandFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        BlockIgniteEvent.IgniteCause cause = event.getCause();
        boolean spreading = cause == BlockIgniteEvent.IgniteCause.SPREAD || cause == BlockIgniteEvent.IgniteCause.LAVA
                || cause == BlockIgniteEvent.IgniteCause.LIGHTNING;
        if (spreading && off(event.getBlock(), IslandFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ explosions

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (!islands.isIslandWorld(entity.getWorld())) {
            return;
        }
        Island island = islands.islandAt(Location.locToBlock(entity.getX()), Location.locToBlock(entity.getZ()));
        if (island == null || !islands.flagOn(island, IslandFlag.EXPLOSIONS)) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (off(event.getBlock(), IslandFlag.EXPLOSIONS)) {
            event.blockList().clear();
        }
    }

    // ------------------------------------------------------------------ leaves

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        if (off(event.getBlock(), IslandFlag.LEAF_DECAY)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ trampling

    /** Players stepping on farmland: refused silently when the island has trampling off (for everyone, also members). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTrample(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() == Action.PHYSICAL && block != null && block.getType() == Material.FARMLAND
                && off(block, IslandFlag.CROP_TRAMPLING)) {
            event.setCancelled(true);
        }
    }

    /** Mobs stepping on farmland. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityTrample(EntityInteractEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.FARMLAND && off(block, IslandFlag.CROP_TRAMPLING)) {
            event.setCancelled(true);
        }
    }

    /** Backup for anything else that turns farmland into dirt (falling on it while riding, etc.). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.FARMLAND && event.getTo() == Material.DIRT && islandAt(block) != null
                && off(block, IslandFlag.CROP_TRAMPLING)) {
            event.setCancelled(true);
        }
    }
}
